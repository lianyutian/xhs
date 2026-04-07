package com.lianyutian.xhs.user.service.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lianyutian.xhs.user.model.domain.VerificationCodeStatus;
import com.lianyutian.xhs.user.model.domain.VerificationCodePurpose;
import com.lianyutian.xhs.user.repository.mybatis.VerificationCodeMapper;
import com.lianyutian.xhs.user.support.AbstractDbIntegrationTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class VerificationWorkflowTest extends AbstractDbIntegrationTest {

    @Autowired
    private DefaultVerificationCodeService service;

    @Autowired
    private VerificationCodeMapper verificationCodeMapper;

    @Test
    void shouldReplaceOldEmailCodeAndConsumeOnSuccess() {
        String first = service.issueEmailCode(VerificationCodePurpose.REGISTER, "a@example.com");
        String second = service.issueEmailCode(VerificationCodePurpose.REGISTER, "a@example.com");

        assertThat(service.verifyEmailCode(VerificationCodePurpose.REGISTER, "a@example.com", first)).isFalse();
        assertThat(service.verifyEmailCode(VerificationCodePurpose.REGISTER, "a@example.com", second)).isTrue();
        assertThat(service.verifyEmailCode(VerificationCodePurpose.REGISTER, "a@example.com", second)).isFalse();
    }

    @Test
    void shouldFreezeCodeAfterTooManyAttempts() {
        String code = service.issueEmailCode(VerificationCodePurpose.REGISTER, "b@example.com");
        assertThat(service.verifyEmailCode(VerificationCodePurpose.REGISTER, "b@example.com", "wrong")).isFalse();
        assertThat(service.verifyEmailCode(VerificationCodePurpose.REGISTER, "b@example.com", "wrong")).isFalse();
        assertThat(service.verifyEmailCode(VerificationCodePurpose.REGISTER, "b@example.com", "wrong")).isFalse();
        assertThat(service.verifyEmailCode(VerificationCodePurpose.REGISTER, "b@example.com", "wrong")).isFalse();
        assertThat(service.verifyEmailCode(VerificationCodePurpose.REGISTER, "b@example.com", "wrong")).isFalse();
        assertThat(service.verifyEmailCode(VerificationCodePurpose.REGISTER, "b@example.com", code)).isFalse();
    }

    @Test
    void shouldIssueAndConsumeImageCaptcha() {
        ImageCaptchaChallenge challenge = service.createImageCaptcha("LOGIN");
        assertThat(challenge.imageContent()).doesNotContain(challenge.answer());
        assertThat(challenge.imageContent()).startsWith("data:image/png;base64,");

        assertThat(service.verifyImageCaptcha(challenge.token(), challenge.answer())).isTrue();
        assertThat(service.verifyImageCaptcha(challenge.token(), challenge.answer())).isFalse();
    }

    @Test
    void shouldRejectUnsupportedImageCaptchaScenario() {
        assertThatThrownBy(() -> service.createImageCaptcha("UPLOAD"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("INVALID_CAPTCHA_SCENARIO");
    }

    @Test
    void shouldPersistSentStatusAndStillAllowVerification() {
        String code = service.issueEmailCode(VerificationCodePurpose.REGISTER, "sent@example.com");
        String status = jdbcTemplate.queryForObject(
            "select status from verification_code where target='sent@example.com' order by id desc limit 1",
            String.class
        );
        assertThat(status).isEqualTo("SENT");
        assertThat(service.verifyEmailCode(VerificationCodePurpose.REGISTER, "sent@example.com", code)).isTrue();
    }

    @Test
    void shouldNotFallbackToOlderVerifiableCodeWhenLatestAlreadyConsumed() {
        String target = "fallback-block@example.com";
        String oldCode = service.issueEmailCode(VerificationCodePurpose.REGISTER, target);

        jdbcTemplate.update(
            """
            insert into verification_code(
                purpose, target, code_hash, status, attempt_count, max_attempts, expires_at, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, DATEADD('MINUTE', 10, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """,
            VerificationCodePurpose.REGISTER.name(),
            target,
            "not-used",
            "CONSUMED",
            0,
            5
        );

        assertThat(service.verifyEmailCode(VerificationCodePurpose.REGISTER, target, oldCode)).isFalse();
    }

    @Test
    void shouldReplaceOlderPendingSendCodeWhenIssuingNewCode() {
        String target = "pending-replaced@example.com";
        jdbcTemplate.update(
            """
            insert into verification_code(
                purpose, target, code_hash, status, attempt_count, max_attempts, expires_at, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, DATEADD('MINUTE', 10, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """,
            VerificationCodePurpose.REGISTER.name(),
            target,
            "stale",
            "PENDING_SEND",
            0,
            5
        );

        service.issueEmailCode(VerificationCodePurpose.REGISTER, target);

        String oldStatus = jdbcTemplate.queryForObject(
            "select status from verification_code where target = ? order by id asc limit 1",
            String.class,
            target
        );
        assertThat(oldStatus).isEqualTo("REPLACED");
    }

    @Test
    void shouldRejectStaleSendResultUpdateWhenCodeAlreadyReplaced() {
        String target = "stale-send-result@example.com";
        insertPendingCode(target, "old");
        insertPendingCode(target, "new");

        Long oldId = jdbcTemplate.queryForObject(
            "select min(id) from verification_code where target = ?",
            Long.class,
            target
        );
        Long latestId = jdbcTemplate.queryForObject(
            "select max(id) from verification_code where target = ?",
            Long.class,
            target
        );
        verificationCodeMapper.markOlderVerifiableAsReplaced(
            VerificationCodePurpose.REGISTER.name(),
            target,
            latestId,
            OffsetDateTime.now(ZoneOffset.UTC)
        );

        int updatedRows = verificationCodeMapper.updateSendResultIfPending(
            oldId,
            VerificationCodeStatus.SENT,
            OffsetDateTime.now(ZoneOffset.UTC)
        );

        String oldStatus = jdbcTemplate.queryForObject(
            "select status from verification_code where id = ?",
            String.class,
            oldId
        );
        assertThat(updatedRows).isZero();
        assertThat(oldStatus).isEqualTo("REPLACED");
    }

    @Test
    void shouldPersistSendFailureEvenWhenCodeAlreadyReplaced() {
        String target = "stale-send-failure@example.com";
        insertPendingCode(target, "old");
        insertPendingCode(target, "new");

        Long oldId = jdbcTemplate.queryForObject(
            "select min(id) from verification_code where target = ?",
            Long.class,
            target
        );
        Long latestId = jdbcTemplate.queryForObject(
            "select max(id) from verification_code where target = ?",
            Long.class,
            target
        );
        verificationCodeMapper.markOlderVerifiableAsReplaced(
            VerificationCodePurpose.REGISTER.name(),
            target,
            latestId,
            OffsetDateTime.now(ZoneOffset.UTC)
        );

        int updatedRows = verificationCodeMapper.markSendFailedIfPendingOrReplaced(
            oldId,
            OffsetDateTime.now(ZoneOffset.UTC)
        );

        String oldStatus = jdbcTemplate.queryForObject(
            "select status from verification_code where id = ?",
            String.class,
            oldId
        );
        assertThat(updatedRows).isEqualTo(1);
        assertThat(oldStatus).isEqualTo("SEND_FAILED");
    }

    @Test
    void shouldAllowSendResultUpdateWhenCodeStillPendingSend() {
        String target = "pending-send-update@example.com";
        insertPendingCode(target, "new");

        Long id = jdbcTemplate.queryForObject(
            "select max(id) from verification_code where target = ?",
            Long.class,
            target
        );
        int updatedRows = verificationCodeMapper.updateSendResultIfPending(
            id,
            VerificationCodeStatus.SENT,
            OffsetDateTime.now(ZoneOffset.UTC)
        );

        String status = jdbcTemplate.queryForObject(
            "select status from verification_code where id = ?",
            String.class,
            id
        );
        assertThat(updatedRows).isEqualTo(1);
        assertThat(status).isEqualTo("SENT");
    }

    @Test
    void shouldNotConsumeExpiredOrAttemptExhaustedCodeAtSqlLevel() {
        String target = "consume-guard@example.com";
        jdbcTemplate.update(
            """
            insert into verification_code(
                purpose, target, code_hash, status, attempt_count, max_attempts, expires_at, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, DATEADD('SECOND', -1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """,
            VerificationCodePurpose.REGISTER.name(),
            target,
            "expired",
            VerificationCodeStatus.SENT.name(),
            0,
            5
        );
        Long expiredId = jdbcTemplate.queryForObject(
            "select max(id) from verification_code where target = ?",
            Long.class,
            target
        );

        int expiredConsumed = verificationCodeMapper.consumeIfVerifiable(
            expiredId,
            OffsetDateTime.now(ZoneOffset.UTC)
        );
        assertThat(expiredConsumed).isEqualTo(0);

        jdbcTemplate.update(
            """
            insert into verification_code(
                purpose, target, code_hash, status, attempt_count, max_attempts, expires_at, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, DATEADD('MINUTE', 10, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """,
            VerificationCodePurpose.REGISTER.name(),
            target,
            "frozen-by-attempt",
            VerificationCodeStatus.SENT.name(),
            5,
            5
        );
        Long exhaustedId = jdbcTemplate.queryForObject(
            "select max(id) from verification_code where target = ?",
            Long.class,
            target
        );

        int exhaustedConsumed = verificationCodeMapper.consumeIfVerifiable(
            exhaustedId,
            OffsetDateTime.now(ZoneOffset.UTC)
        );
        assertThat(exhaustedConsumed).isEqualTo(0);
    }

    private void insertPendingCode(String target, String codeHash) {
        jdbcTemplate.update(
            """
            insert into verification_code(
                purpose, target, code_hash, status, attempt_count, max_attempts, expires_at, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, DATEADD('MINUTE', 10, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """,
            VerificationCodePurpose.REGISTER.name(),
            target,
            codeHash,
            VerificationCodeStatus.PENDING_SEND.name(),
            0,
            5
        );
    }
}
