package com.lianyutian.xhs.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lianyutian.xhs.user.controller.request.AddressUpsertRequest;
import com.lianyutian.xhs.user.controller.response.AddressResponse;
import com.lianyutian.xhs.user.controller.response.ApiResponse;
import com.lianyutian.xhs.user.model.domain.VerificationCodePurpose;
import com.lianyutian.xhs.user.service.auth.AuthTokens;
import com.lianyutian.xhs.user.service.auth.DefaultAuthService;
import com.lianyutian.xhs.user.service.verification.DefaultVerificationCodeService;
import com.lianyutian.xhs.user.support.AbstractDbIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AddressControllerContractTest extends AbstractDbIntegrationTest {

    @Autowired
    private AddressController addressController;

    @Autowired
    private DefaultVerificationCodeService verificationCodeService;

    @Autowired
    private DefaultAuthService authService;

    @Test
    void shouldCreateListUpdateAndDeleteAddressesForCurrentUser() {
        String accessToken = issueAccessToken("address-owner@example.com");

        ApiResponse<AddressResponse> firstCreate = addressController.createAddress(
            "Bearer " + accessToken,
            request("hangzhou xihu road 1", false)
        );
        assertThat(firstCreate.code()).isEqualTo("OK");
        assertThat(firstCreate.data().addressId()).isNotNull();
        assertThat(firstCreate.data().defaultAddress()).isTrue();
        Long firstAddressId = firstCreate.data().addressId();

        ApiResponse<AddressResponse> secondCreate = addressController.createAddress(
            "Bearer " + accessToken,
            request("hangzhou xihu road 2", false)
        );
        assertThat(secondCreate.code()).isEqualTo("OK");
        assertThat(secondCreate.data().defaultAddress()).isFalse();
        Long secondAddressId = secondCreate.data().addressId();

        ApiResponse<List<AddressResponse>> beforeUpdateList = addressController.listAddresses("Bearer " + accessToken);
        assertThat(beforeUpdateList.data()).extracting(AddressResponse::addressId)
            .containsExactly(firstAddressId, secondAddressId);

        ApiResponse<AddressResponse> updateResponse = addressController.updateAddress(
            "Bearer " + accessToken,
            secondAddressId,
            request("hangzhou xihu road 9", true)
        );
        assertThat(updateResponse.code()).isEqualTo("OK");
        assertThat(updateResponse.data().addressId()).isEqualTo(secondAddressId);
        assertThat(updateResponse.data().detailAddress()).isEqualTo("hangzhou xihu road 9");
        assertThat(updateResponse.data().defaultAddress()).isTrue();

        ApiResponse<List<AddressResponse>> afterUpdateList = addressController.listAddresses("Bearer " + accessToken);
        assertThat(afterUpdateList.data()).extracting(AddressResponse::addressId)
            .containsExactly(secondAddressId, firstAddressId);
        assertThat(afterUpdateList.data()).extracting(AddressResponse::defaultAddress)
            .containsExactly(true, false);

        ApiResponse<Void> deleteResponse = addressController.deleteAddress("Bearer " + accessToken, secondAddressId);
        assertThat(deleteResponse.code()).isEqualTo("OK");

        ApiResponse<List<AddressResponse>> afterDeleteList = addressController.listAddresses("Bearer " + accessToken);
        assertThat(afterDeleteList.data()).extracting(AddressResponse::addressId)
            .containsExactly(firstAddressId);
        assertThat(afterDeleteList.data().get(0).defaultAddress()).isTrue();
    }

    @Test
    void shouldRejectAddressUpdateWhenAddressBelongsToAnotherUser() {
        String victimToken = issueAccessToken("victim@example.com");
        ApiResponse<AddressResponse> created = addressController.createAddress(
            "Bearer " + victimToken,
            request("victim road", true)
        );

        String attackerToken = issueAccessToken("attacker@example.com");
        assertThatThrownBy(() -> addressController.updateAddress(
            "Bearer " + attackerToken,
            created.data().addressId(),
            request("hijack road", true)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("OWNERSHIP_VIOLATION");
    }

    @Test
    void shouldBackfillDefaultWhenCurrentDefaultUpdatedToNonDefault() {
        String accessToken = issueAccessToken("update-default-backfill@example.com");

        Long firstAddressId = addressController.createAddress(
            "Bearer " + accessToken,
            request("road-first", false)
        ).data().addressId();
        Long secondAddressId = addressController.createAddress(
            "Bearer " + accessToken,
            request("road-second", false)
        ).data().addressId();

        addressController.updateAddress(
            "Bearer " + accessToken,
            secondAddressId,
            request("road-second-default", true)
        );
        addressController.updateAddress(
            "Bearer " + accessToken,
            secondAddressId,
            request("road-second-not-default", false)
        );

        ApiResponse<List<AddressResponse>> listed = addressController.listAddresses("Bearer " + accessToken);
        assertThat(listed.data()).extracting(AddressResponse::addressId)
            .containsExactly(firstAddressId, secondAddressId);
        assertThat(listed.data()).extracting(AddressResponse::defaultAddress)
            .containsExactly(true, false);
    }

    @Test
    void shouldRejectCreateWhenAddressLimitExceeded() {
        String accessToken = issueAccessToken("address-limit@example.com");
        for (int i = 0; i < 10; i++) {
            ApiResponse<AddressResponse> response = addressController.createAddress(
                "Bearer " + accessToken,
                request("road-" + i, false)
            );
            assertThat(response.code()).isEqualTo("OK");
        }

        assertThatThrownBy(() -> addressController.createAddress(
            "Bearer " + accessToken,
            request("road-over-limit", false)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("ADDRESS_LIMIT_EXCEEDED");
    }

    private AddressUpsertRequest request(String detailAddress, boolean defaultAddress) {
        return new AddressUpsertRequest(
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

    private String issueAccessToken(String email) {
        String code = verificationCodeService.issueEmailCode(VerificationCodePurpose.REGISTER, email);
        authService.register(email, "Password123!", code);
        AuthTokens tokens = authService.login(email, "Password123!", "5.5.5.5", "agent");
        return tokens.accessToken();
    }
}
