package com.lianyutian.xhs.user.service.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lianyutian.xhs.user.model.domain.UserAddress;
import com.lianyutian.xhs.user.model.domain.VerificationCodePurpose;
import com.lianyutian.xhs.user.service.auth.DefaultAuthService;
import com.lianyutian.xhs.user.service.verification.DefaultVerificationCodeService;
import com.lianyutian.xhs.user.support.AbstractDbIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AddressWorkflowTest extends AbstractDbIntegrationTest {

    @Autowired
    private DefaultAddressService addressService;

    @Autowired
    private DefaultVerificationCodeService verificationCodeService;

    @Autowired
    private DefaultAuthService authService;

    @Test
    void shouldCreateUpdateDeleteAndListAddressesInWorkflow() {
        Long userId = issueUserId("address-workflow@example.com");

        UserAddress first = addressService.create(userId, command("road-1", false));
        UserAddress second = addressService.create(userId, command("road-2", false));
        assertThat(first.defaultAddress()).isTrue();
        assertThat(second.defaultAddress()).isFalse();

        List<UserAddress> beforeUpdate = addressService.list(userId);
        assertThat(beforeUpdate).extracting(UserAddress::addressId)
            .containsExactly(first.addressId(), second.addressId());

        UserAddress switched = addressService.update(userId, second.addressId(), command("road-2-default", true));
        assertThat(switched.defaultAddress()).isTrue();
        assertThat(switched.detailAddress()).isEqualTo("road-2-default");

        List<UserAddress> afterSwitch = addressService.list(userId);
        assertThat(afterSwitch).extracting(UserAddress::addressId)
            .containsExactly(second.addressId(), first.addressId());
        assertThat(afterSwitch).extracting(UserAddress::defaultAddress)
            .containsExactly(true, false);

        addressService.delete(userId, second.addressId());
        List<UserAddress> afterDelete = addressService.list(userId);
        assertThat(afterDelete).extracting(UserAddress::addressId)
            .containsExactly(first.addressId());
        assertThat(afterDelete.get(0).defaultAddress()).isTrue();
    }

    @Test
    void shouldBackfillDefaultWhenCurrentDefaultUpdatedToFalse() {
        Long userId = issueUserId("address-update-backfill@example.com");

        UserAddress first = addressService.create(userId, command("road-a", false));
        UserAddress second = addressService.create(userId, command("road-b", false));
        addressService.update(userId, second.addressId(), command("road-b-default", true));

        UserAddress updated = addressService.update(userId, second.addressId(), command("road-b-not-default", false));
        assertThat(updated.defaultAddress()).isFalse();

        List<UserAddress> listed = addressService.list(userId);
        assertThat(listed).extracting(UserAddress::addressId)
            .containsExactly(first.addressId(), second.addressId());
        assertThat(listed).extracting(UserAddress::defaultAddress)
            .containsExactly(true, false);
    }

    @Test
    void shouldRejectCreateWhenAddressLimitExceeded() {
        Long userId = issueUserId("address-limit-workflow@example.com");
        for (int i = 0; i < 10; i++) {
            UserAddress created = addressService.create(userId, command("road-limit-" + i, false));
            assertThat(created.addressId()).isNotNull();
        }

        assertThatThrownBy(() -> addressService.create(userId, command("road-limit-over", false)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("ADDRESS_LIMIT_EXCEEDED");

        assertThat(addressService.list(userId)).hasSize(10);
    }

    private AddressCommand command(String detailAddress, boolean defaultAddress) {
        return new AddressCommand(
            "name",
            "13800000000",
            "zhejiang",
            "hangzhou",
            "xihu",
            detailAddress,
            "310000",
            defaultAddress
        );
    }

    private Long issueUserId(String email) {
        String code = verificationCodeService.issueEmailCode(VerificationCodePurpose.REGISTER, email);
        authService.register(email, "Password123!", code);
        return jdbcTemplate.queryForObject("select id from user_account where email = ?", Long.class, email);
    }
}
