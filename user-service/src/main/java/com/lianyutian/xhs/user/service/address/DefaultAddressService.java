package com.lianyutian.xhs.user.service.address;

import com.lianyutian.xhs.user.model.domain.UserAddress;
import com.lianyutian.xhs.user.model.entity.UserAddressEntity;
import com.lianyutian.xhs.user.repository.mybatis.UserAccountMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserAddressMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户地址服务默认实现，提供地址的增删改查及默认地址管理逻辑
 */
public class DefaultAddressService implements AddressService {

    private static final int MAX_EFFECTIVE_ADDRESS_COUNT = 10;

    private final UserAccountMapper userAccountMapper;
    private final UserAddressMapper userAddressMapper;

    public DefaultAddressService(UserAccountMapper userAccountMapper, UserAddressMapper userAddressMapper) {
        this.userAccountMapper = userAccountMapper;
        this.userAddressMapper = userAddressMapper;
    }

    /**
     * 创建新的收货地址
     *
     * @param ownerUserId 所属用户 ID
     * @param command 包含地址信息的业务命令对象
     * @return 新创建的地址领域模型
     * @throws IllegalArgumentException 当地址数量超过限制时抛出异常
     * @throws IllegalStateException 当数据库插入失败时抛出异常
     */
    @Override
    @Transactional
    public UserAddress create(Long ownerUserId, AddressCommand command) {
        requireOwnerAndCommand(ownerUserId, command);
        lockOwnerUserRow(ownerUserId);

        // 检查有效地址数量是否达到上限
        int effectiveCount = userAddressMapper.countEffectiveByOwnerUserId(ownerUserId);
        if (effectiveCount >= MAX_EFFECTIVE_ADDRESS_COUNT) {
            throw new IllegalArgumentException("ADDRESS_LIMIT_EXCEEDED");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // 确定是否为默认地址：如果是第一个地址或显式指定，则设为默认并清除旧默认标记
        boolean shouldBeDefault = effectiveCount == 0 || command.defaultAddress();
        if (shouldBeDefault && effectiveCount > 0) {
            userAddressMapper.clearDefaultByOwnerUserId(ownerUserId, now);
        }

        UserAddressEntity toCreate = new UserAddressEntity(
            null,
            ownerUserId,
            command.recipientName(),
            command.recipientPhone(),
            command.province(),
            command.city(),
            command.district(),
            command.detailAddress(),
            command.postalCode(),
            shouldBeDefault,
            now,
            now,
            null
        );
        int inserted = userAddressMapper.insert(toCreate);
        if (inserted != 1 || toCreate.getAddressId() == null) {
            throw new IllegalStateException("ADDRESS_CREATE_FAILED");
        }
        return toDomain(toCreate);
    }

    /**
     * 更新指定的收货地址（全量替换）
     *
     * @param ownerUserId 所属用户 ID
     * @param addressId 待更新的地址 ID
     * @param command 包含新地址信息的业务命令对象
     * @return 更新后的地址领域模型
     * @throws IllegalArgumentException 当所有权校验失败时抛出异常
     */
    @Override
    @Transactional
    public UserAddress update(Long ownerUserId, Long addressId, AddressCommand command) {
        requireOwnerAndCommand(ownerUserId, command);
        Objects.requireNonNull(addressId, "addressId must not be null");
        lockOwnerUserRow(ownerUserId);

        UserAddressEntity current = requireOwnedEntity(ownerUserId, addressId);
        UserAddressEntity fallbackCandidate = null;
        boolean shouldBeDefault = command.defaultAddress();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // 处理默认地址逻辑：如果设为默认则清除旧的；如果取消默认且是最后一个默认，需寻找替补
        if (command.defaultAddress()) {
            userAddressMapper.clearDefaultByOwnerUserId(ownerUserId, now);
        } else if (Boolean.TRUE.equals(current.getDefaultAddress())) {
            fallbackCandidate = selectDefaultFallback(ownerUserId, addressId);
            shouldBeDefault = fallbackCandidate == null;
        }

        UserAddressEntity toUpdate = new UserAddressEntity(
            addressId,
            ownerUserId,
            command.recipientName(),
            command.recipientPhone(),
            command.province(),
            command.city(),
            command.district(),
            command.detailAddress(),
            command.postalCode(),
            shouldBeDefault,
            current.getCreatedAt(),
            now,
            null
        );
        int updated = userAddressMapper.updateByAddressIdAndOwner(toUpdate);
        if (updated != 1) {
            throw new IllegalArgumentException("OWNERSHIP_VIOLATION");
        }

        // 如果有替补地址，将其提升为默认地址
        if (fallbackCandidate != null) {
            promoteDefault(ownerUserId, fallbackCandidate, now);
        }
        return toDomain(toUpdate);
    }

    /**
     * 删除指定的收货地址（软删除）
     *
     * @param ownerUserId 所属用户 ID
     * @param addressId 待删除的地址 ID
     * @throws IllegalArgumentException 当所有权校验失败时抛出异常
     */
    @Override
    @Transactional
    public void delete(Long ownerUserId, Long addressId) {
        Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        Objects.requireNonNull(addressId, "addressId must not be null");
        lockOwnerUserRow(ownerUserId);

        UserAddressEntity current = requireOwnedEntity(ownerUserId, addressId);
        UserAddressEntity fallbackCandidate = null;

        // 如果删除的是默认地址，需要寻找下一个有效地址作为替补默认
        if (Boolean.TRUE.equals(current.getDefaultAddress())) {
            fallbackCandidate = selectDefaultFallback(ownerUserId, addressId);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int deleted = userAddressMapper.softDeleteByAddressIdAndOwner(addressId, ownerUserId, now, now);
        if (deleted != 1) {
            throw new IllegalArgumentException("OWNERSHIP_VIOLATION");
        }

        // 提升替补地址为默认地址
        if (fallbackCandidate != null) {
            promoteDefault(ownerUserId, fallbackCandidate, now);
        }
    }

    /**
     * 获取当前用户的所有有效地址列表
     *
     * @param ownerUserId 所属用户 ID
     * @return 地址领域模型列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<UserAddress> list(Long ownerUserId) {
        Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        return userAddressMapper.findEffectiveByOwnerUserId(ownerUserId)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    /**
     * 校验并获取属于指定用户的地址（用于写操作前的权限校验）
     *
     * @param ownerUserId 所属用户 ID
     * @param addressId 地址 ID
     * @return 地址领域模型
     * @throws IllegalArgumentException 当地址不存在或不属于该用户时抛出异常
     */
    @Override
    @Transactional(readOnly = true)
    public UserAddress requireOwnedAddress(Long ownerUserId, Long addressId) {
        Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        Objects.requireNonNull(addressId, "addressId must not be null");
        return toDomain(requireOwnedEntity(ownerUserId, addressId));
    }

    /**
     * 校验地址是否存在且属于指定用户，返回实体对象
     */
    private UserAddressEntity requireOwnedEntity(Long ownerUserId, Long addressId) {
        UserAddressEntity found = userAddressMapper.findEffectiveByAddressId(addressId);
        if (found == null || !ownerUserId.equals(found.getOwnerUserId())) {
            throw new IllegalArgumentException("OWNERSHIP_VIOLATION");
        }
        return found;
    }

    /**
     * 从用户的有效地址中选择一个非排除项作为默认地址的候补
     */
    private UserAddressEntity selectDefaultFallback(Long ownerUserId, Long excludedAddressId) {
        return userAddressMapper.findEffectiveByOwnerUserId(ownerUserId)
            .stream()
            .filter(entity -> !excludedAddressId.equals(entity.getAddressId()))
            .findFirst()
            .orElse(null);
    }

    /**
     * 将指定的候选地址提升为默认地址
     */
    private void promoteDefault(Long ownerUserId, UserAddressEntity candidate, OffsetDateTime now) {
        UserAddressEntity toPromote = new UserAddressEntity(
            candidate.getAddressId(),
            ownerUserId,
            candidate.getRecipientName(),
            candidate.getRecipientPhone(),
            candidate.getProvince(),
            candidate.getCity(),
            candidate.getDistrict(),
            candidate.getDetailAddress(),
            candidate.getPostalCode(),
            true,
            candidate.getCreatedAt(),
            now,
            null
        );
        int promoted = userAddressMapper.updateByAddressIdAndOwner(toPromote);
        if (promoted != 1) {
            throw new IllegalArgumentException("OWNERSHIP_VIOLATION");
        }
    }

    /**
     * 将持久层实体对象转换为领域模型对象
     */
    private UserAddress toDomain(UserAddressEntity entity) {
        return new UserAddress(
            entity.getAddressId(),
            entity.getOwnerUserId(),
            entity.getRecipientName(),
            entity.getRecipientPhone(),
            entity.getProvince(),
            entity.getCity(),
            entity.getDistrict(),
            entity.getDetailAddress(),
            entity.getPostalCode(),
            Boolean.TRUE.equals(entity.getDefaultAddress()),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    /**
     * 校验用户 ID 和业务命令对象不为空
     */
    private void requireOwnerAndCommand(Long ownerUserId, AddressCommand command) {
        Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        Objects.requireNonNull(command, "command must not be null");
    }

    /**
     * 在写操作前对用户主记录加行锁，避免并发请求破坏地址不变量。
     */
    private void lockOwnerUserRow(Long ownerUserId) {
        Long lockedUserId = userAccountMapper.lockById(ownerUserId);
        if (lockedUserId == null) {
            throw new IllegalArgumentException("UNAUTHORIZED");
        }
    }
}
