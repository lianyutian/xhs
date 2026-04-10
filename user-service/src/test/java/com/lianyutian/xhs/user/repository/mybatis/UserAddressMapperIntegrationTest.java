package com.lianyutian.xhs.user.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.lianyutian.xhs.user.model.entity.UserAddressEntity;
import com.lianyutian.xhs.user.support.TestOverrideConfig;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestOverrideConfig.class)
class UserAddressMapperIntegrationTest {

    @Autowired
    private UserAddressMapper userAddressMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("delete from user_address");
        jdbcTemplate.update("delete from user_account");
    }

    @Test
    void shouldInsertUpdateAndFindEffectiveByAddressId() {
        Long ownerUserId = 9101L;
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        UserAddressEntity entity = buildAddress(
            ownerUserId,
            "alice",
            "13812345678",
            "zhejiang",
            "hangzhou",
            "xihu",
            "xihu avenue 88",
            "310000",
            false,
            createdAt,
            createdAt
        );

        int inserted = userAddressMapper.insert(entity);

        assertThat(inserted).isEqualTo(1);
        assertThat(entity.getAddressId()).isNotNull();

        UserAddressEntity found = userAddressMapper.findEffectiveByAddressId(entity.getAddressId());
        assertThat(found).isNotNull();
        assertThat(found.getOwnerUserId()).isEqualTo(ownerUserId);
        assertThat(found.getRecipientName()).isEqualTo("alice");
        assertThat(found.getRecipientPhone()).isEqualTo("13812345678");
        assertThat(found.getProvince()).isEqualTo("zhejiang");
        assertThat(found.getCity()).isEqualTo("hangzhou");
        assertThat(found.getDistrict()).isEqualTo("xihu");
        assertThat(found.getDetailAddress()).isEqualTo("xihu avenue 88");
        assertThat(found.getPostalCode()).isEqualTo("310000");
        assertThat(found.getDefaultAddress()).isFalse();
        assertThat(found.getDeletedAt()).isNull();

        OffsetDateTime updatedAt = createdAt.plusDays(1);
        entity.setRecipientName("alice-updated");
        entity.setRecipientPhone("13912345678");
        entity.setProvince("jiangsu");
        entity.setCity("nanjing");
        entity.setDistrict("gulou");
        entity.setDetailAddress("gulou road 66");
        entity.setPostalCode("210000");
        entity.setDefaultAddress(true);
        entity.setUpdatedAt(updatedAt);

        int updated = userAddressMapper.updateByAddressIdAndOwner(entity);

        assertThat(updated).isEqualTo(1);
        UserAddressEntity refreshed = userAddressMapper.findEffectiveByAddressId(entity.getAddressId());
        assertThat(refreshed).isNotNull();
        assertThat(refreshed.getRecipientName()).isEqualTo("alice-updated");
        assertThat(refreshed.getRecipientPhone()).isEqualTo("13912345678");
        assertThat(refreshed.getProvince()).isEqualTo("jiangsu");
        assertThat(refreshed.getCity()).isEqualTo("nanjing");
        assertThat(refreshed.getDistrict()).isEqualTo("gulou");
        assertThat(refreshed.getDetailAddress()).isEqualTo("gulou road 66");
        assertThat(refreshed.getPostalCode()).isEqualTo("210000");
        assertThat(refreshed.getDefaultAddress()).isTrue();
    }

    @Test
    void shouldCountAndListOnlyEffectiveAddressesWithStableOrder() {
        Long ownerUserId = 9201L;
        OffsetDateTime base = OffsetDateTime.parse("2026-01-02T00:00:00Z");
        UserAddressEntity defaultAddress = buildAddress(
            ownerUserId,
            "default-user",
            "13800000001",
            "zhejiang",
            "hangzhou",
            "xihu",
            "road 1",
            "310001",
            true,
            base,
            base.plusMinutes(1)
        );
        UserAddressEntity latestAddress = buildAddress(
            ownerUserId,
            "latest-user",
            "13800000002",
            "zhejiang",
            "hangzhou",
            "gongshu",
            "road 2",
            "310002",
            false,
            base,
            base.plusMinutes(3)
        );
        UserAddressEntity deletedAddress = buildAddress(
            ownerUserId,
            "deleted-user",
            "13800000003",
            "zhejiang",
            "hangzhou",
            "binjiang",
            "road 3",
            "310003",
            false,
            base,
            base.plusMinutes(2)
        );
        UserAddressEntity otherOwnerAddress = buildAddress(
            9999L,
            "other-user",
            "13800000004",
            "zhejiang",
            "hangzhou",
            "xiaoshan",
            "road 4",
            "310004",
            false,
            base,
            base.plusMinutes(4)
        );

        userAddressMapper.insert(defaultAddress);
        userAddressMapper.insert(latestAddress);
        userAddressMapper.insert(deletedAddress);
        userAddressMapper.insert(otherOwnerAddress);
        int deleted = userAddressMapper.softDeleteByAddressIdAndOwner(
            deletedAddress.getAddressId(),
            ownerUserId,
            base.plusHours(1),
            base.plusHours(1)
        );

        assertThat(deleted).isEqualTo(1);
        assertThat(userAddressMapper.countEffectiveByOwnerUserId(ownerUserId)).isEqualTo(2);

        List<UserAddressEntity> addresses = userAddressMapper.findEffectiveByOwnerUserId(ownerUserId);
        assertThat(addresses).extracting(UserAddressEntity::getAddressId)
            .containsExactly(defaultAddress.getAddressId(), latestAddress.getAddressId());
        assertThat(userAddressMapper.findEffectiveByAddressId(deletedAddress.getAddressId())).isNull();
    }

    @Test
    void shouldClearDefaultFlagForEffectiveAddresses() {
        Long ownerUserId = 9301L;
        OffsetDateTime base = OffsetDateTime.parse("2026-01-03T00:00:00Z");
        UserAddressEntity first = buildAddress(
            ownerUserId,
            "first",
            "13800000011",
            "guangdong",
            "shenzhen",
            "nanshan",
            "street 11",
            "518000",
            true,
            base,
            base.plusMinutes(1)
        );
        UserAddressEntity second = buildAddress(
            ownerUserId,
            "second",
            "13800000012",
            "guangdong",
            "shenzhen",
            "futian",
            "street 12",
            "518001",
            false,
            base,
            base.plusMinutes(3)
        );

        userAddressMapper.insert(first);
        userAddressMapper.insert(second);

        int cleared = userAddressMapper.clearDefaultByOwnerUserId(ownerUserId, base.plusHours(3));
        assertThat(cleared).isEqualTo(1);

        List<UserAddressEntity> addresses = userAddressMapper.findEffectiveByOwnerUserId(ownerUserId);
        assertThat(addresses).extracting(UserAddressEntity::getDefaultAddress)
            .containsOnly(false);
    }

    @Test
    void shouldHideSoftDeletedAddressFromOwnershipLookup() {
        Long ownerUserId = 9401L;
        OffsetDateTime base = OffsetDateTime.parse("2026-01-04T00:00:00Z");
        UserAddressEntity entity = buildAddress(
            ownerUserId,
            "owner-check",
            "13800000021",
            "zhejiang",
            "ningbo",
            "haishu",
            "street 21",
            "315000",
            false,
            base,
            base
        );

        userAddressMapper.insert(entity);

        Long activeOwner = userAddressMapper.findOwnerUserIdByAddressId(entity.getAddressId());
        assertThat(activeOwner).isEqualTo(ownerUserId);

        int deleted = userAddressMapper.softDeleteByAddressIdAndOwner(
            entity.getAddressId(),
            ownerUserId,
            base.plusHours(1),
            base.plusHours(1)
        );
        assertThat(deleted).isEqualTo(1);

        Long deletedOwner = userAddressMapper.findOwnerUserIdByAddressId(entity.getAddressId());
        assertThat(deletedOwner).isNull();
    }

    private UserAddressEntity buildAddress(
        Long ownerUserId,
        String recipientName,
        String recipientPhone,
        String province,
        String city,
        String district,
        String detailAddress,
        String postalCode,
        boolean defaultAddress,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
        UserAddressEntity entity = new UserAddressEntity();
        entity.setOwnerUserId(ownerUserId);
        entity.setRecipientName(recipientName);
        entity.setRecipientPhone(recipientPhone);
        entity.setProvince(province);
        entity.setCity(city);
        entity.setDistrict(district);
        entity.setDetailAddress(detailAddress);
        entity.setPostalCode(postalCode);
        entity.setDefaultAddress(defaultAddress);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(updatedAt);
        return entity;
    }
}
