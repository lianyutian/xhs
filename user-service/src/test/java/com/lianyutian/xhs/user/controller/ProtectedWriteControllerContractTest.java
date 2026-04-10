package com.lianyutian.xhs.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lianyutian.xhs.user.controller.request.AddressUpsertRequest;
import com.lianyutian.xhs.user.controller.request.AddressWriteRequest;
import com.lianyutian.xhs.user.controller.request.UploadRequest;
import com.lianyutian.xhs.user.controller.response.AddressResponse;
import com.lianyutian.xhs.user.service.auth.AuthTokens;
import com.lianyutian.xhs.user.service.auth.DefaultAuthService;
import com.lianyutian.xhs.user.controller.response.ApiResponse;
import com.lianyutian.xhs.user.model.domain.VerificationCodePurpose;
import com.lianyutian.xhs.user.repository.mybatis.UserAddressMapper;
import com.lianyutian.xhs.user.service.address.AddressService;
import com.lianyutian.xhs.user.support.AbstractDbIntegrationTest;
import com.lianyutian.xhs.user.service.verification.DefaultVerificationCodeService;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProtectedWriteControllerContractTest extends AbstractDbIntegrationTest {

    @Autowired
    private ProtectedWriteController protectedWriteController;

    @Autowired
    private AddressController addressController;

    @Autowired
    private DefaultVerificationCodeService verificationCodeService;

    @Autowired
    private DefaultAuthService authService;

    @Test
    void shouldAllowAddressWriteWhenOwnerMatchesAuthenticatedUser() {
        String accessToken = issueAccessToken("write-ok@example.com");
        Long addressId = createAddress(accessToken, "write-ok-address");
        ApiResponse<Void> response = protectedWriteController.writeAddress(
            "Bearer " + accessToken,
            new AddressWriteRequest(addressId)
        );
        assertThat(response.code()).isEqualTo("OK");
    }

    @Test
    void shouldRejectAddressWriteWhenOwnerDoesNotMatch() {
        String attackerAccessToken = issueAccessToken("write-deny@example.com");
        String ownerAccessToken = issueAccessToken("write-real-owner@example.com");
        Long addressId = createAddress(ownerAccessToken, "owner-only-address");

        assertThatThrownBy(() -> protectedWriteController.writeAddress(
            "Bearer " + attackerAccessToken,
            new AddressWriteRequest(addressId)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("OWNERSHIP_VIOLATION");
    }

    @Test
    void shouldRejectAddressWriteWhenClientSpoofsOwnerButAddressBelongsToAnotherUser() {
        String accessToken = issueAccessToken("attacker@example.com");
        String victimAccessToken = issueAccessToken("victim@example.com");
        Long victimAddressId = createAddress(victimAccessToken, "victim-address");

        assertThatThrownBy(() -> protectedWriteController.writeAddress(
            "Bearer " + accessToken,
            new AddressWriteRequest(victimAddressId)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("OWNERSHIP_VIOLATION");
    }

    @Test
    void shouldRecordOwnershipFailureWhenAddressMissing() {
        String accessToken = issueAccessToken("missing-address@example.com");

        assertThatThrownBy(() -> protectedWriteController.writeAddress(
            "Bearer " + accessToken,
            new AddressWriteRequest(99999L)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("OWNERSHIP_VIOLATION");

        Integer ownershipFailed = jdbcTemplate.queryForObject(
            "select count(*) from security_event where event_type='OWNERSHIP_FAILED' and detail='resource=address:99999'",
            Integer.class
        );
        assertThat(ownershipFailed).isEqualTo(1);
    }

    @Test
    void shouldTreatSoftDeletedAddressAsOwnershipViolationWithEvent() {
        String accessToken = issueAccessToken("deleted-address@example.com");
        Long addressId = createAddress(accessToken, "to-delete");
        addressController.deleteAddress("Bearer " + accessToken, addressId);

        assertThatThrownBy(() -> protectedWriteController.writeAddress(
            "Bearer " + accessToken,
            new AddressWriteRequest(addressId)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("OWNERSHIP_VIOLATION");

        Integer ownershipFailed = jdbcTemplate.queryForObject(
            "select count(*) from security_event where event_type='OWNERSHIP_FAILED' and detail=?",
            Integer.class,
            "resource=address:" + addressId
        );
        assertThat(ownershipFailed).isEqualTo(1);
    }

    @Test
    void shouldDependOnAddressServiceInsteadOfMapperForOwnershipLookup() {
        Field[] fields = ProtectedWriteController.class.getDeclaredFields();
        boolean hasAddressServiceField = false;
        boolean hasMapperField = false;
        for (Field field : fields) {
            if (field.getType() == AddressService.class) {
                hasAddressServiceField = true;
            }
            if (field.getType() == UserAddressMapper.class) {
                hasMapperField = true;
            }
        }
        assertThat(hasAddressServiceField).isTrue();
        assertThat(hasMapperField).isFalse();
    }

    @Test
    void shouldApplyUploadPolicyByAuthenticatedUser() {
        String policyAccessToken = issueAccessToken("upload-policy@example.com");
        assertThatThrownBy(() -> protectedWriteController.upload(
            "Bearer " + policyAccessToken,
            new UploadRequest("application/pdf", 1024)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("UPLOAD_TYPE_REJECTED");

        assertThatThrownBy(() -> protectedWriteController.upload(
            "Bearer " + policyAccessToken,
            new UploadRequest("image/jpeg", 6 * 1024 * 1024L)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("UPLOAD_SIZE_REJECTED");

        String rateAccessToken = issueAccessToken("upload-rate@example.com");
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> protectedWriteController.upload(
                "Bearer " + rateAccessToken,
                new UploadRequest("application/pdf", 1024)
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UPLOAD_TYPE_REJECTED");
        }

        assertThatThrownBy(() -> protectedWriteController.upload(
            "Bearer " + rateAccessToken,
            new UploadRequest("image/png", 2048)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("UPLOAD_RATE_LIMITED");

        String successAccessToken = issueAccessToken("upload-success@example.com");
        for (int i = 0; i < 10; i++) {
            ApiResponse<Void> response = protectedWriteController.upload(
                "Bearer " + successAccessToken,
                new UploadRequest("image/png", 2048)
            );
            assertThat(response.code()).isEqualTo("OK");
        }
        assertThatThrownBy(() -> protectedWriteController.upload(
            "Bearer " + successAccessToken,
            new UploadRequest("image/png", 2048)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("UPLOAD_RATE_LIMITED");
    }

    private String issueAccessToken(String email) {
        String code = verificationCodeService.issueEmailCode(VerificationCodePurpose.REGISTER, email);
        authService.register(email, "Password123!", code);
        AuthTokens tokens = authService.login(email, "Password123!", "8.8.8.8", "agent");
        return tokens.accessToken();
    }

    private Long createAddress(String accessToken, String detailAddress) {
        ApiResponse<AddressResponse> response = addressController.createAddress(
            "Bearer " + accessToken,
            new AddressUpsertRequest(
                "name",
                "13800000000",
                "zhejiang",
                "hangzhou",
                "xihu",
                detailAddress,
                "310000",
                false
            )
        );
        return response.data().addressId();
    }
}
