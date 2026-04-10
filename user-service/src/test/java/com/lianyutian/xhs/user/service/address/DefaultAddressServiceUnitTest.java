package com.lianyutian.xhs.user.service.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lianyutian.xhs.user.model.domain.UserAddress;
import com.lianyutian.xhs.user.model.entity.UserAddressEntity;
import com.lianyutian.xhs.user.repository.mybatis.UserAddressMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserAccountMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class DefaultAddressServiceUnitTest {

    @Test
    void shouldDependOnUserAccountMapperForOwnerRowLocking() {
        boolean hasUserAccountMapperField = Arrays.stream(DefaultAddressService.class.getDeclaredFields())
            .anyMatch(field -> field.getType() == UserAccountMapper.class);
        assertThat(hasUserAccountMapperField).isTrue();
    }

    @Test
    void shouldLockOwnerRowBeforeCreateMutation() {
        UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
        UserAddressMapper userAddressMapper = mock(UserAddressMapper.class);
        when(userAccountMapper.lockById(1L)).thenReturn(1L);
        when(userAddressMapper.countEffectiveByOwnerUserId(1L)).thenReturn(0);
        doAnswer(invocation -> {
            UserAddressEntity entity = invocation.getArgument(0);
            entity.setAddressId(101L);
            return 1;
        }).when(userAddressMapper).insert(any(UserAddressEntity.class));
        DefaultAddressService service = new DefaultAddressService(userAccountMapper, userAddressMapper);

        service.create(1L, command(false));

        InOrder order = inOrder(userAccountMapper, userAddressMapper);
        order.verify(userAccountMapper).lockById(1L);
        order.verify(userAddressMapper).countEffectiveByOwnerUserId(1L);
    }

    @Test
    void shouldLockOwnerRowBeforeUpdateAndDeleteMutation() {
        UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
        UserAddressMapper userAddressMapper = mock(UserAddressMapper.class);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UserAddressEntity existing = addressEntity(11L, 1L, false, now);
        when(userAccountMapper.lockById(1L)).thenReturn(1L);
        when(userAddressMapper.findEffectiveByAddressId(11L)).thenReturn(existing);
        when(userAddressMapper.updateByAddressIdAndOwner(any(UserAddressEntity.class))).thenReturn(1);
        when(userAddressMapper.softDeleteByAddressIdAndOwner(eq(11L), eq(1L), any(), any())).thenReturn(1);
        DefaultAddressService service = new DefaultAddressService(userAccountMapper, userAddressMapper);

        service.update(1L, 11L, command(false));
        service.delete(1L, 11L);

        InOrder order = inOrder(userAccountMapper, userAddressMapper);
        order.verify(userAccountMapper).lockById(1L);
        order.verify(userAddressMapper).findEffectiveByAddressId(11L);
        order.verify(userAddressMapper).updateByAddressIdAndOwner(any(UserAddressEntity.class));
        order.verify(userAccountMapper).lockById(1L);
        order.verify(userAddressMapper).findEffectiveByAddressId(11L);
        order.verify(userAddressMapper).softDeleteByAddressIdAndOwner(eq(11L), eq(1L), any(), any());
    }

    @Test
    void shouldForceFirstAddressAsDefaultEvenWhenRequestDisablesDefault() {
        UserAddressMapper userAddressMapper = mock(UserAddressMapper.class);
        when(userAddressMapper.countEffectiveByOwnerUserId(1L)).thenReturn(0);
        doAnswer(invocation -> {
            UserAddressEntity entity = invocation.getArgument(0);
            entity.setAddressId(101L);
            return 1;
        }).when(userAddressMapper).insert(any(UserAddressEntity.class));
        DefaultAddressService service = createService(userAddressMapper);

        UserAddress created = service.create(1L, command(false));

        ArgumentCaptor<UserAddressEntity> insertCaptor = ArgumentCaptor.forClass(UserAddressEntity.class);
        verify(userAddressMapper).insert(insertCaptor.capture());
        assertThat(insertCaptor.getValue().getDefaultAddress()).isTrue();
        assertThat(created.defaultAddress()).isTrue();
        verify(userAddressMapper, never()).clearDefaultByOwnerUserId(any(), any());
    }

    @Test
    void shouldRejectAddressCreationWhenEffectiveAddressLimitExceeded() {
        UserAddressMapper userAddressMapper = mock(UserAddressMapper.class);
        when(userAddressMapper.countEffectiveByOwnerUserId(1L)).thenReturn(10);
        DefaultAddressService service = createService(userAddressMapper);

        assertThatThrownBy(() -> service.create(1L, command(false)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("ADDRESS_LIMIT_EXCEEDED");
    }

    @Test
    void shouldClearPreviousDefaultWhenCreatingNewDefaultAddress() {
        UserAddressMapper userAddressMapper = mock(UserAddressMapper.class);
        when(userAddressMapper.countEffectiveByOwnerUserId(1L)).thenReturn(2);
        doAnswer(invocation -> {
            UserAddressEntity entity = invocation.getArgument(0);
            entity.setAddressId(202L);
            return 1;
        }).when(userAddressMapper).insert(any(UserAddressEntity.class));
        DefaultAddressService service = createService(userAddressMapper);

        UserAddress created = service.create(1L, command(true));

        verify(userAddressMapper).clearDefaultByOwnerUserId(eq(1L), any());
        assertThat(created.defaultAddress()).isTrue();
    }

    @Test
    void shouldSwitchDefaultToTargetAddressOnUpdate() {
        UserAddressMapper userAddressMapper = mock(UserAddressMapper.class);
        UserAddressEntity existing = addressEntity(11L, 1L, false, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        when(userAddressMapper.findEffectiveByAddressId(11L)).thenReturn(existing);
        when(userAddressMapper.updateByAddressIdAndOwner(any(UserAddressEntity.class))).thenReturn(1);
        DefaultAddressService service = createService(userAddressMapper);

        UserAddress updated = service.update(1L, 11L, command(true));

        ArgumentCaptor<UserAddressEntity> updateCaptor = ArgumentCaptor.forClass(UserAddressEntity.class);
        verify(userAddressMapper).updateByAddressIdAndOwner(updateCaptor.capture());
        verify(userAddressMapper).clearDefaultByOwnerUserId(eq(1L), any());
        assertThat(updateCaptor.getValue().getDefaultAddress()).isTrue();
        assertThat(updated.defaultAddress()).isTrue();
    }

    @Test
    void shouldKeepSingleAddressAsDefaultWhenRequestTriesToCancelDefault() {
        UserAddressMapper userAddressMapper = mock(UserAddressMapper.class);
        UserAddressEntity existing = addressEntity(12L, 1L, true, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(3));
        when(userAddressMapper.findEffectiveByAddressId(12L)).thenReturn(existing);
        when(userAddressMapper.findEffectiveByOwnerUserId(1L)).thenReturn(List.of(existing));
        when(userAddressMapper.updateByAddressIdAndOwner(any(UserAddressEntity.class))).thenReturn(1);
        DefaultAddressService service = createService(userAddressMapper);

        UserAddress updated = service.update(1L, 12L, command(false));

        ArgumentCaptor<UserAddressEntity> updateCaptor = ArgumentCaptor.forClass(UserAddressEntity.class);
        verify(userAddressMapper).updateByAddressIdAndOwner(updateCaptor.capture());
        verify(userAddressMapper, never()).clearDefaultByOwnerUserId(any(), any());
        assertThat(updateCaptor.getValue().getDefaultAddress()).isTrue();
        assertThat(updated.defaultAddress()).isTrue();
    }

    @Test
    void shouldBackfillDefaultWhenCurrentDefaultIsUpdatedToNonDefault() {
        UserAddressMapper userAddressMapper = mock(UserAddressMapper.class);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UserAddressEntity target = addressEntity(13L, 1L, true, now.minusMinutes(1));
        UserAddressEntity candidate = addressEntity(14L, 1L, false, now.minusMinutes(2));
        when(userAddressMapper.findEffectiveByAddressId(13L)).thenReturn(target);
        when(userAddressMapper.findEffectiveByOwnerUserId(1L)).thenReturn(List.of(target, candidate));
        when(userAddressMapper.updateByAddressIdAndOwner(any(UserAddressEntity.class))).thenReturn(1);
        DefaultAddressService service = createService(userAddressMapper);

        service.update(1L, 13L, command(false));

        ArgumentCaptor<UserAddressEntity> updateCaptor = ArgumentCaptor.forClass(UserAddressEntity.class);
        verify(userAddressMapper, times(2)).updateByAddressIdAndOwner(updateCaptor.capture());
        assertThat(updateCaptor.getAllValues().get(0).getAddressId()).isEqualTo(13L);
        assertThat(updateCaptor.getAllValues().get(0).getDefaultAddress()).isFalse();
        assertThat(updateCaptor.getAllValues().get(1).getAddressId()).isEqualTo(14L);
        assertThat(updateCaptor.getAllValues().get(1).getDefaultAddress()).isTrue();
    }

    @Test
    void shouldBackfillDefaultWhenDeletingCurrentDefaultAddress() {
        UserAddressMapper userAddressMapper = mock(UserAddressMapper.class);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UserAddressEntity target = addressEntity(15L, 1L, true, now.minusMinutes(1));
        UserAddressEntity candidate = addressEntity(16L, 1L, false, now.minusMinutes(2));
        when(userAddressMapper.findEffectiveByAddressId(15L)).thenReturn(target);
        when(userAddressMapper.findEffectiveByOwnerUserId(1L)).thenReturn(List.of(target, candidate));
        when(userAddressMapper.softDeleteByAddressIdAndOwner(eq(15L), eq(1L), any(), any())).thenReturn(1);
        when(userAddressMapper.updateByAddressIdAndOwner(any(UserAddressEntity.class))).thenReturn(1);
        DefaultAddressService service = createService(userAddressMapper);

        service.delete(1L, 15L);

        verify(userAddressMapper).softDeleteByAddressIdAndOwner(eq(15L), eq(1L), any(), any());
        ArgumentCaptor<UserAddressEntity> updateCaptor = ArgumentCaptor.forClass(UserAddressEntity.class);
        verify(userAddressMapper).updateByAddressIdAndOwner(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getAddressId()).isEqualTo(16L);
        assertThat(updateCaptor.getValue().getDefaultAddress()).isTrue();
    }

    @Test
    void shouldUseUnifiedOwnershipViolationForMissingAndForeignAddress() {
        UserAddressMapper userAddressMapper = mock(UserAddressMapper.class);
        DefaultAddressService service = createService(userAddressMapper);

        when(userAddressMapper.findEffectiveByAddressId(999L)).thenReturn(null);
        assertThatThrownBy(() -> service.update(1L, 999L, command(false)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("OWNERSHIP_VIOLATION");
        assertThatThrownBy(() -> service.delete(1L, 999L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("OWNERSHIP_VIOLATION");
        assertThatThrownBy(() -> service.requireOwnedAddress(1L, 999L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("OWNERSHIP_VIOLATION");

        when(userAddressMapper.findEffectiveByAddressId(888L)).thenReturn(
            addressEntity(888L, 2L, false, OffsetDateTime.now(ZoneOffset.UTC))
        );
        assertThatThrownBy(() -> service.update(1L, 888L, command(false)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("OWNERSHIP_VIOLATION");
        assertThatThrownBy(() -> service.delete(1L, 888L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("OWNERSHIP_VIOLATION");
        assertThatThrownBy(() -> service.requireOwnedAddress(1L, 888L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("OWNERSHIP_VIOLATION");
    }

    @Test
    void shouldListOwnedEffectiveAddresses() {
        UserAddressMapper userAddressMapper = mock(UserAddressMapper.class);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UserAddressEntity first = addressEntity(21L, 1L, true, now.minusMinutes(1));
        UserAddressEntity second = addressEntity(22L, 1L, false, now.minusMinutes(2));
        when(userAddressMapper.findEffectiveByOwnerUserId(1L)).thenReturn(List.of(first, second));
        DefaultAddressService service = createService(userAddressMapper);

        List<UserAddress> listed = service.list(1L);

        assertThat(listed).hasSize(2);
        assertThat(listed.get(0).addressId()).isEqualTo(21L);
        assertThat(listed.get(0).defaultAddress()).isTrue();
        assertThat(listed.get(1).addressId()).isEqualTo(22L);
        assertThat(listed.get(1).defaultAddress()).isFalse();
        verify(userAddressMapper).findEffectiveByOwnerUserId(1L);
    }

    @Test
    void shouldReturnOwnedAddressWhenOwnershipMatches() {
        UserAddressMapper userAddressMapper = mock(UserAddressMapper.class);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(userAddressMapper.findEffectiveByAddressId(31L)).thenReturn(addressEntity(31L, 1L, false, now));
        DefaultAddressService service = createService(userAddressMapper);

        UserAddress owned = service.requireOwnedAddress(1L, 31L);

        assertThat(owned.addressId()).isEqualTo(31L);
        assertThat(owned.ownerUserId()).isEqualTo(1L);
        assertThat(owned.defaultAddress()).isFalse();
    }

    private AddressCommand command(boolean defaultAddress) {
        return new AddressCommand(
            "name",
            "13800000000",
            "zhejiang",
            "hangzhou",
            "xihu",
            "road 1",
            "310000",
            defaultAddress
        );
    }

    private UserAddressEntity addressEntity(Long addressId, Long ownerUserId, boolean defaultAddress, OffsetDateTime updatedAt) {
        UserAddressEntity entity = new UserAddressEntity();
        entity.setAddressId(addressId);
        entity.setOwnerUserId(ownerUserId);
        entity.setRecipientName("name");
        entity.setRecipientPhone("13800000000");
        entity.setProvince("zhejiang");
        entity.setCity("hangzhou");
        entity.setDistrict("xihu");
        entity.setDetailAddress("road 1");
        entity.setPostalCode("310000");
        entity.setDefaultAddress(defaultAddress);
        entity.setCreatedAt(updatedAt.minusDays(1));
        entity.setUpdatedAt(updatedAt);
        return entity;
    }

    private DefaultAddressService createService(UserAddressMapper userAddressMapper) {
        UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
        when(userAccountMapper.lockById(anyLong())).thenAnswer(invocation -> invocation.getArgument(0));
        return new DefaultAddressService(userAccountMapper, userAddressMapper);
    }
}
