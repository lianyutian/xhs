package com.lianyutian.xhs.user.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lianyutian.xhs.user.model.domain.VerificationCodePurpose;
import com.lianyutian.xhs.user.repository.mybatis.UserRefreshTokenMapper;
import com.lianyutian.xhs.user.support.AbstractDbIntegrationTest;
import com.lianyutian.xhs.user.service.verification.DefaultVerificationCodeService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AuthWorkflowTest extends AbstractDbIntegrationTest {

    @Autowired
    private DefaultVerificationCodeService verification;

    @Autowired
    private DefaultAuthService authService;

    @Autowired
    private UserRefreshTokenMapper userRefreshTokenMapper;

    @Test
    void shouldRegisterLoginRefreshLogoutAndReadCurrentUser() {
        String registerCode = verification.issueEmailCode(VerificationCodePurpose.REGISTER, "u@example.com");
        authService.register("u@example.com", "Password123!", registerCode);

        AuthTokens tokens = authService.login("u@example.com", "Password123!", "1.1.1.1", "agent");
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.accessToken().split("\\.")).hasSize(3);

        AuthTokens rotated = authService.refresh(tokens.refreshToken(), "1.1.1.1");
        assertThat(rotated.refreshToken()).isNotEqualTo(tokens.refreshToken());
        assertThat(authService.refresh(tokens.refreshToken(), "1.1.1.1").errorCode()).isEqualTo("REFRESH_TOKEN_REPLAYED");

        assertThat(authService.currentUser(rotated.accessToken()).email()).isEqualTo("u@example.com");
        assertThat(authService.currentUser(rotated.accessToken()).nickname()).isEqualTo("u");
        authService.logout(rotated.accessToken());
        assertThat(authService.refresh(rotated.refreshToken(), "1.1.1.1").errorCode()).isEqualTo("SESSION_REVOKED");
    }

    @Test
    void shouldReturnUnifiedInvalidCredentialError() {
        assertThat(authService.login("missing@example.com", "nope", "1.1.1.1", "agent").errorCode())
            .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void shouldReturnSameErrorForUnknownEmailAndWrongPassword() {
        String email = "known@example.com";
        String registerCode = verification.issueEmailCode(VerificationCodePurpose.REGISTER, email);
        authService.register(email, "Password123!", registerCode);

        String unknownEmailError = authService.login("missing-same@example.com", "Password123!", "1.1.1.1", "agent").errorCode();
        String wrongPasswordError = authService.login(email, "wrong-password", "1.1.1.1", "agent").errorCode();

        assertThat(unknownEmailError).isEqualTo("INVALID_CREDENTIALS");
        assertThat(wrongPasswordError).isEqualTo("INVALID_CREDENTIALS");
        assertThat(wrongPasswordError).isEqualTo(unknownEmailError);
    }

    @Test
    void shouldRequireCaptchaAfterRiskEscalation() {
        assertThat(authService.login("missing@example.com", "nope", "1.1.1.1", "agent").errorCode()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(authService.login("missing@example.com", "nope", "1.1.1.1", "agent").errorCode()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(authService.login("missing@example.com", "nope", "1.1.1.1", "agent").errorCode()).isEqualTo("INVALID_CREDENTIALS");

        assertThat(authService.login("missing@example.com", "nope", "1.1.1.1", "agent").errorCode())
            .isEqualTo("CAPTCHA_REQUIRED");
    }

    @Test
    void shouldTempBlockAfterRepeatedCaptchaMissingInEscalatedStage() {
        String ip = "1.2.3.4";
        String email = "missing-captcha@example.com";

        for (int i = 0; i < 3; i++) {
            assertThat(authService.login(email, "nope", ip, "agent").errorCode())
                .isEqualTo("INVALID_CREDENTIALS");
        }

        for (int i = 0; i < 3; i++) {
            assertThat(authService.login(email, "nope", ip, "agent").errorCode())
                .isEqualTo("CAPTCHA_REQUIRED");
        }

        assertThat(authService.login(email, "nope", ip, "agent").errorCode())
            .isEqualTo("LOGIN_TEMP_BLOCKED");
    }

    @Test
    void shouldReturnTempBlockedAfterFailuresContinueBeyondCaptchaStage() {
        String ip = "4.4.4.4";
        String email = "missing-temp@example.com";
        for (int i = 0; i < 3; i++) {
            assertThat(authService.login(email, "nope", ip, "agent").errorCode()).isEqualTo("INVALID_CREDENTIALS");
        }

        for (int i = 0; i < 3; i++) {
            var challenge = verification.createImageCaptcha("LOGIN");
            assertThat(authService.login(
                email,
                "nope",
                ip,
                "agent",
                challenge.token(),
                challenge.answer()
            ).errorCode()).isEqualTo("INVALID_CREDENTIALS");
        }

        assertThat(authService.login(email, "nope", ip, "agent").errorCode()).isEqualTo("LOGIN_TEMP_BLOCKED");
    }

    @Test
    void shouldRejectCaptchaFromDifferentScenarioWhenLoginEscalated() {
        String ip = "10.10.10.10";
        String email = "cross-scenario@example.com";
        for (int i = 0; i < 3; i++) {
            assertThat(authService.login(email, "nope", ip, "agent").errorCode())
                .isEqualTo("INVALID_CREDENTIALS");
        }

        var registerCaptcha = verification.createImageCaptcha("REGISTER");
        assertThat(authService.login(
            email,
            "nope",
            ip,
            "agent",
            registerCaptcha.token(),
            registerCaptcha.answer()
        ).errorCode()).isEqualTo("CAPTCHA_REQUIRED");
    }

    @Test
    void shouldRejectExpiredAccessAndRefreshToken() {
        String registerCode = verification.issueEmailCode(VerificationCodePurpose.REGISTER, "expired@example.com");
        authService.register("expired@example.com", "Password123!", registerCode);
        AuthTokens tokens = authService.login("expired@example.com", "Password123!", "1.1.1.1", "agent");

        jdbcTemplate.update("update user_refresh_token set expires_at = DATEADD('SECOND', -1, CURRENT_TIMESTAMP)");
        jdbcTemplate.update("update user_session set expires_at = DATEADD('SECOND', -1, CURRENT_TIMESTAMP)");

        assertThat(authService.refresh(tokens.refreshToken(), "1.1.1.1").errorCode()).isEqualTo("INVALID_REFRESH_TOKEN");
        assertThatThrownBy(() -> authService.currentUser(tokens.accessToken()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("UNAUTHORIZED");
    }

    @Test
    void shouldReturnRefreshRateLimitedWhenRefreshAttemptsAreTooFrequent() {
        for (int i = 0; i < 8; i++) {
            assertThat(authService.refresh("non-existent-token", "5.5.5.5").errorCode())
                .isEqualTo("INVALID_REFRESH_TOKEN");
        }
        assertThat(authService.refresh("non-existent-token", "5.5.5.5").errorCode())
            .isEqualTo("REFRESH_RATE_LIMITED");
    }

    @Test
    void shouldRevokeSessionWhenRefreshTokenBelongsToMissingAccount() {
        String email = "missing-account-refresh@example.com";
        String registerCode = verification.issueEmailCode(VerificationCodePurpose.REGISTER, email);
        authService.register(email, "Password123!", registerCode);
        AuthTokens tokens = authService.login(email, "Password123!", "11.11.11.11", "agent");

        jdbcTemplate.update("delete from user_profile where user_id in (select id from user_account where email = ?)", email);
        jdbcTemplate.update("delete from user_account where email = ?", email);

        assertThat(authService.refresh(tokens.refreshToken(), "11.11.11.11").errorCode()).isEqualTo("SESSION_REVOKED");
        String status = jdbcTemplate.queryForObject(
            "select status from user_session order by id desc limit 1",
            String.class
        );
        assertThat(status).isEqualTo("REVOKED");
    }

    @Test
    void shouldRejectRefreshWhenSessionAlreadyExpiredEvenIfStatusStillActive() {
        String email = "expired-session-refresh@example.com";
        String registerCode = verification.issueEmailCode(VerificationCodePurpose.REGISTER, email);
        authService.register(email, "Password123!", registerCode);
        AuthTokens tokens = authService.login(email, "Password123!", "12.12.12.12", "agent");

        jdbcTemplate.update(
            "update user_session set status='ACTIVE', expires_at = DATEADD('SECOND', -1, CURRENT_TIMESTAMP) where id = (select max(id) from user_session)"
        );

        assertThat(authService.refresh(tokens.refreshToken(), "12.12.12.12").errorCode()).isEqualTo("SESSION_REVOKED");

        String sessionStatus = jdbcTemplate.queryForObject(
            "select status from user_session order by id desc limit 1",
            String.class
        );
        Integer activeRefreshCount = jdbcTemplate.queryForObject(
            "select count(*) from user_refresh_token where status = 'ACTIVE'",
            Integer.class
        );
        assertThat(sessionStatus).isEqualTo("EXPIRED");
        assertThat(activeRefreshCount).isEqualTo(0);
    }

    @Test
    void shouldNotTransitionExpiredRefreshTokenToReplacedAtSqlLevel() {
        String email = "sql-refresh-guard@example.com";
        String registerCode = verification.issueEmailCode(VerificationCodePurpose.REGISTER, email);
        authService.register(email, "Password123!", registerCode);
        authService.login(email, "Password123!", "13.13.13.13", "agent");

        Long refreshId = jdbcTemplate.queryForObject(
            "select id from user_refresh_token order by id desc limit 1",
            Long.class
        );
        jdbcTemplate.update(
            "update user_refresh_token set status='ACTIVE', expires_at = DATEADD('SECOND', -1, CURRENT_TIMESTAMP) where id = ?",
            refreshId
        );

        int updated = userRefreshTokenMapper.transitionToReplacedIfActive(refreshId, OffsetDateTime.now(ZoneOffset.UTC));
        assertThat(updated).isEqualTo(0);
    }

    @Test
    void shouldRejectAccessTokenWithUnexpectedIssuer() {
        String registerCode = verification.issueEmailCode(VerificationCodePurpose.REGISTER, "issuer@example.com");
        authService.register("issuer@example.com", "Password123!", registerCode);
        AuthTokens tokens = authService.login("issuer@example.com", "Password123!", "2.2.2.2", "agent");

        SecretKey key = Keys.hmacShaKeyFor("test-secret-at-least-32-characters-long".getBytes(StandardCharsets.UTF_8));
        var payload = Jwts.parser().verifyWith(key).build().parseSignedClaims(tokens.accessToken()).getPayload();
        Long sessionId = payload.get("sid", Long.class);
        String subject = payload.getSubject();
        String forgedToken = Jwts.builder()
            .subject(subject)
            .issuer("another-issuer")
            .claim("sid", sessionId)
            .claim("email", "issuer@example.com")
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now().plusSeconds(60)))
            .signWith(key)
            .compact();

        assertThatThrownBy(() -> authService.currentUser(forgedToken))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("UNAUTHORIZED");
    }

    @Test
    void shouldPersistVerificationAttemptWhenRegisterFails() {
        String email = "register-fail@example.com";
        verification.issueEmailCode(VerificationCodePurpose.REGISTER, email);

        assertThatThrownBy(() -> authService.register(email, "Password123!", "wrong-code"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("INVALID_REGISTER_CODE");

        Integer attemptCount = jdbcTemplate.queryForObject(
            "select attempt_count from verification_code where target = ? order by id desc limit 1",
            Integer.class,
            email
        );
        String status = jdbcTemplate.queryForObject(
            "select status from verification_code where target = ? order by id desc limit 1",
            String.class,
            email
        );
        assertThat(attemptCount).isEqualTo(1);
        assertThat(status).isEqualTo("ACTIVE");
    }

    @Test
    void shouldRollbackCodeConsumptionWhenRegisterTransactionFailsAfterVerification() {
        String email = "invalid-email";
        String code = verification.issueEmailCode(VerificationCodePurpose.REGISTER, email);

        assertThatThrownBy(() -> authService.register(email, "Password123!", code))
            .isInstanceOf(StringIndexOutOfBoundsException.class);

        Integer accountCount = jdbcTemplate.queryForObject(
            "select count(*) from user_account where email = ?",
            Integer.class,
            email
        );
        String status = jdbcTemplate.queryForObject(
            "select status from verification_code where target = ? order by id desc limit 1",
            String.class,
            email
        );

        assertThat(accountCount).isEqualTo(0);
        assertThat(status).isEqualTo("SENT");
        assertThat(verification.verifyEmailCode(VerificationCodePurpose.REGISTER, email, code)).isTrue();
    }

    @Test
    void shouldRejectCurrentUserWhenAccountBecomesLocked() {
        String email = "locked-current-user@example.com";
        String registerCode = verification.issueEmailCode(VerificationCodePurpose.REGISTER, email);
        authService.register(email, "Password123!", registerCode);
        AuthTokens tokens = authService.login(email, "Password123!", "15.15.15.15", "agent");

        jdbcTemplate.update("update user_account set status='LOCKED' where email = ?", email);

        assertThatThrownBy(() -> authService.currentUser(tokens.accessToken()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("UNAUTHORIZED");
    }

    @Test
    void shouldRevokeSessionWhenAccountBecomesLockedBeforeRefresh() {
        String email = "locked-refresh@example.com";
        String registerCode = verification.issueEmailCode(VerificationCodePurpose.REGISTER, email);
        authService.register(email, "Password123!", registerCode);
        AuthTokens tokens = authService.login(email, "Password123!", "16.16.16.16", "agent");

        jdbcTemplate.update("update user_account set status='LOCKED' where email = ?", email);

        assertThat(authService.refresh(tokens.refreshToken(), "16.16.16.16").errorCode()).isEqualTo("SESSION_REVOKED");

        String sessionStatus = jdbcTemplate.queryForObject(
            "select status from user_session order by id desc limit 1",
            String.class
        );
        Integer activeRefreshCount = jdbcTemplate.queryForObject(
            "select count(*) from user_refresh_token where status = 'ACTIVE'",
            Integer.class
        );
        assertThat(sessionStatus).isEqualTo("REVOKED");
        assertThat(activeRefreshCount).isEqualTo(0);
    }
}
