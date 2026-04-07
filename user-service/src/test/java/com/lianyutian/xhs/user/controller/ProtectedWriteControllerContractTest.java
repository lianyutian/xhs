package com.lianyutian.xhs.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lianyutian.xhs.user.controller.request.AddressWriteRequest;
import com.lianyutian.xhs.user.controller.request.UploadRequest;
import com.lianyutian.xhs.user.service.auth.AuthTokens;
import com.lianyutian.xhs.user.service.auth.DefaultAuthService;
import com.lianyutian.xhs.user.controller.response.ApiResponse;
import com.lianyutian.xhs.user.model.domain.VerificationCodePurpose;
import com.lianyutian.xhs.user.support.AbstractDbIntegrationTest;
import com.lianyutian.xhs.user.service.verification.DefaultVerificationCodeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProtectedWriteControllerContractTest extends AbstractDbIntegrationTest {

    @Autowired
    private ProtectedWriteController protectedWriteController;

    @Autowired
    private DefaultVerificationCodeService verificationCodeService;

    @Autowired
    private DefaultAuthService authService;

    @Test
    void shouldAllowAddressWriteWhenOwnerMatchesAuthenticatedUser() {
        String accessToken = issueAccessToken("write-ok@example.com");
        Long userId = resolveUserId("write-ok@example.com");
        jdbcTemplate.update("insert into user_address(address_id, owner_user_id) values (?, ?)", 1001L, userId);
        ApiResponse<Void> response = protectedWriteController.writeAddress(
            "Bearer " + accessToken,
            new AddressWriteRequest(1001L)
        );
        assertThat(response.code()).isEqualTo("OK");
    }

    @Test
    void shouldRejectAddressWriteWhenOwnerDoesNotMatch() {
        String accessToken = issueAccessToken("write-deny@example.com");
        Long realOwnerUserId = issueUser("write-real-owner@example.com");
        jdbcTemplate.update("insert into user_address(address_id, owner_user_id) values (?, ?)", 2002L, realOwnerUserId);
        assertThatThrownBy(() -> protectedWriteController.writeAddress(
            "Bearer " + accessToken,
            new AddressWriteRequest(2002L)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("OWNERSHIP_VIOLATION");
    }

    @Test
    void shouldRejectAddressWriteWhenClientSpoofsOwnerButAddressBelongsToAnotherUser() {
        String accessToken = issueAccessToken("attacker@example.com");
        Long victimUserId = issueUser("victim@example.com");
        jdbcTemplate.update("insert into user_address(address_id, owner_user_id) values (?, ?)", 3003L, victimUserId);

        assertThatThrownBy(() -> protectedWriteController.writeAddress(
            "Bearer " + accessToken,
            new AddressWriteRequest(3003L)
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

    private Long resolveUserId(String email) {
        return jdbcTemplate.queryForObject("select id from user_account where email = ?", Long.class, email);
    }

    private Long issueUser(String email) {
        String code = verificationCodeService.issueEmailCode(VerificationCodePurpose.REGISTER, email);
        authService.register(email, "Password123!", code);
        return resolveUserId(email);
    }
}
