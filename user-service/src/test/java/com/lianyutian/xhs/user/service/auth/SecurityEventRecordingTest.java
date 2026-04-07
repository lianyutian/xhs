package com.lianyutian.xhs.user.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.lianyutian.xhs.user.model.domain.VerificationCodePurpose;
import com.lianyutian.xhs.user.support.AbstractDbIntegrationTest;
import com.lianyutian.xhs.user.service.verification.DefaultVerificationCodeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SecurityEventRecordingTest extends AbstractDbIntegrationTest {

    @Autowired
    private DefaultVerificationCodeService verification;

    @Autowired
    private DefaultAuthService authService;

    @Test
    void shouldRecordLoginFailureAndRefreshReplayEvents() {
        authService.login("missing@example.com", "bad", "1.1.1.1", "agent");

        String registerCode = verification.issueEmailCode(VerificationCodePurpose.REGISTER, "u2@example.com");
        authService.register("u2@example.com", "Password123!", registerCode);
        AuthTokens tokens = authService.login("u2@example.com", "Password123!", "1.1.1.1", "agent");
        AuthTokens rotated = authService.refresh(tokens.refreshToken(), "1.1.1.1");
        authService.refresh(tokens.refreshToken(), "1.1.1.1");
        authService.logout(rotated.accessToken());

        Integer loginFailed = jdbcTemplate.queryForObject("select count(*) from security_event where event_type='LOGIN_FAILED'", Integer.class);
        Integer replay = jdbcTemplate.queryForObject("select count(*) from security_event where event_type='REFRESH_REPLAY_DETECTED'", Integer.class);
        Integer revoked = jdbcTemplate.queryForObject("select count(*) from security_event where event_type='SESSION_REVOKED'", Integer.class);

        assertThat(loginFailed).isGreaterThanOrEqualTo(1);
        assertThat(replay).isGreaterThanOrEqualTo(1);
        assertThat(revoked).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldKeepInternalLoginFailureReasonsSeparated() {
        authService.login("absent@example.com", "bad", "2.2.2.2", "agent");

        String registerCode = verification.issueEmailCode(VerificationCodePurpose.REGISTER, "reason@example.com");
        authService.register("reason@example.com", "Password123!", registerCode);
        authService.login("reason@example.com", "wrong-password", "2.2.2.2", "agent");
        jdbcTemplate.update("update user_account set status='LOCKED' where email='reason@example.com'");
        authService.login("reason@example.com", "Password123!", "2.2.2.2", "agent");

        assertThat(jdbcTemplate.queryForList(
            "select detail from security_event where event_type='LOGIN_FAILED'",
            String.class
        )).contains("account_not_found", "password_mismatch", "account_status_locked");
    }

    @Test
    void shouldRecordRefreshReplayEventForExpiredRefreshTokenReuse() {
        String registerCode = verification.issueEmailCode(VerificationCodePurpose.REGISTER, "expired-replay@example.com");
        authService.register("expired-replay@example.com", "Password123!", registerCode);
        AuthTokens tokens = authService.login("expired-replay@example.com", "Password123!", "3.3.3.3", "agent");
        jdbcTemplate.update(
            "update user_refresh_token set status='EXPIRED', expires_at = DATEADD('SECOND', -1, CURRENT_TIMESTAMP) where token_hash is not null"
        );

        assertThat(authService.refresh(tokens.refreshToken(), "3.3.3.3").errorCode()).isEqualTo("INVALID_REFRESH_TOKEN");

        Integer replayEvents = jdbcTemplate.queryForObject(
            "select count(*) from security_event where event_type='REFRESH_REPLAY_DETECTED' and detail='token_status_expired'",
            Integer.class
        );
        assertThat(replayEvents).isGreaterThanOrEqualTo(1);
    }
}
