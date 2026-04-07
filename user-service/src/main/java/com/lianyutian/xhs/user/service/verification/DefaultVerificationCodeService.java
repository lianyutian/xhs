package com.lianyutian.xhs.user.service.verification;

import com.google.code.kaptcha.Producer;
import com.lianyutian.xhs.user.model.domain.VerificationCodePurpose;
import com.lianyutian.xhs.user.model.domain.VerificationCodeStatus;
import com.lianyutian.xhs.user.service.event.SecurityEvent;
import com.lianyutian.xhs.user.service.event.SecurityEventRecorder;
import com.lianyutian.xhs.user.service.integration.mail.MailSenderAdapter;
import com.lianyutian.xhs.user.model.entity.VerificationCodeEntity;
import com.lianyutian.xhs.user.repository.mybatis.VerificationCodeMapper;
import com.lianyutian.xhs.user.service.risk.RedisKeyHelper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class DefaultVerificationCodeService implements VerificationCodeService {

    private static final Set<String> SUPPORTED_CAPTCHA_SCENARIOS = Set.of("LOGIN", "REGISTER");
    private static final Logger log = LoggerFactory.getLogger(DefaultVerificationCodeService.class);

    private final MailSenderAdapter mailSenderAdapter;
    private final SecurityEventRecorder eventRecorder;
    private final int maxAttempts;
    private final Duration imageCaptchaTtl;
    private final Duration emailCodeTtl;
    private final StringRedisTemplate redisTemplate;
    private final RedisKeyHelper redisKeyHelper;
    private final VerificationCodeMapper verificationCodeMapper;
    private final Producer imageCaptchaProducer;

    public DefaultVerificationCodeService(
        MailSenderAdapter mailSenderAdapter,
        int maxAttempts,
        SecurityEventRecorder eventRecorder,
        Duration imageCaptchaTtl,
        Duration emailCodeTtl,
        StringRedisTemplate redisTemplate,
        RedisKeyHelper redisKeyHelper,
        VerificationCodeMapper verificationCodeMapper,
        Producer imageCaptchaProducer
    ) {
        this.mailSenderAdapter = mailSenderAdapter;
        this.maxAttempts = maxAttempts;
        this.eventRecorder = eventRecorder;
        this.imageCaptchaTtl = imageCaptchaTtl;
        this.emailCodeTtl = emailCodeTtl;
        this.redisTemplate = redisTemplate;
        this.redisKeyHelper = redisKeyHelper;
        this.verificationCodeMapper = verificationCodeMapper;
        this.imageCaptchaProducer = imageCaptchaProducer;
    }

    public ImageCaptchaChallenge createImageCaptcha(String scenario) {
        String normalizedScenario = scenario == null ? "" : scenario.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_CAPTCHA_SCENARIOS.contains(normalizedScenario)) {
            throw new IllegalArgumentException("INVALID_CAPTCHA_SCENARIO");
        }
        String token = UUID.randomUUID().toString();
        String answer = imageCaptchaProducer.createText();

        log.info("Generated captcha for scenario %s: %s".formatted(normalizedScenario, answer));
        log.info("Captcha token: %s".formatted(token));

        BufferedImage image = imageCaptchaProducer.createImage(answer);
        String imageContent = "data:image/png;base64," + encodePngBase64(image);
        redisTemplate.opsForValue().set(
            redisKeyHelper.imageCaptcha(token),
            normalizedScenario + ":" + hash(answer),
            imageCaptchaTtl
        );
        return new ImageCaptchaChallenge(token, imageContent, answer);
    }

    public boolean verifyImageCaptcha(String token, String answer) {
        return verifyImageCaptcha(token, answer, null);
    }

    public boolean verifyImageCaptcha(String token, String answer, String expectedScenario) {
        String storedValue = redisTemplate.opsForValue().getAndDelete(redisKeyHelper.imageCaptcha(token));
        if (storedValue == null) {
            return false;
        }
        int separator = storedValue.indexOf(':');
        if (separator <= 0 || separator == storedValue.length() - 1) {
            return false;
        }
        String storedScenario = storedValue.substring(0, separator);
        String storedHash = storedValue.substring(separator + 1);
        String normalizedExpectedScenario = normalizeScenario(expectedScenario);
        if (normalizedExpectedScenario != null && !normalizedExpectedScenario.equals(storedScenario)) {
            return false;
        }
        return storedHash.equals(hash(answer));
    }

    public String issueEmailCode(VerificationCodePurpose purpose, String target) {
        return issueEmailCodeInternal(purpose, target).code();
    }

    public EmailCodeIssueReceipt issueEmailCodeWithReceipt(VerificationCodePurpose purpose, String target) {
        IssuedEmailCode issued = issueEmailCodeInternal(purpose, target);
        String requestId = issued.requestId() == null ? UUID.randomUUID().toString() : issued.requestId().toString();
        return new EmailCodeIssueReceipt(requestId, issued.expiresAt(), issued.sent());
    }

    public EmailCodeConsumeAttempt attemptConsumeEmailCodeInCurrentTransaction(VerificationCodePurpose purpose, String target, String code) {
        VerificationEvaluation evaluation = evaluateCode(purpose, target, code);
        if (evaluation.decision() != VerificationDecision.MATCH) {
            return EmailCodeConsumeAttempt.failure(toFailureContext(evaluation));
        }
        boolean consumed = verificationCodeMapper.consumeIfVerifiable(evaluation.record().getId(), OffsetDateTime.now(ZoneOffset.UTC)) == 1;
        if (consumed) {
            return EmailCodeConsumeAttempt.success();
        }
        return EmailCodeConsumeAttempt.failure(
            new VerificationFailureContext(evaluation.record().getId(), VerificationFailureReason.NOT_VERIFIABLE)
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedEmailCodeAttempt(VerificationCodePurpose purpose, String target, String code) {
        VerificationEvaluation evaluation = evaluateCode(purpose, target, code);
        applyFailureUpdate(toFailureContext(evaluation), OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedEmailCodeAttempt(VerificationFailureContext failureContext) {
        applyFailureUpdate(failureContext, OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean verifyEmailCode(VerificationCodePurpose purpose, String target, String code) {
        VerificationEvaluation evaluation = evaluateCode(purpose, target, code);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (evaluation.decision() != VerificationDecision.MATCH) {
            applyFailureUpdate(toFailureContext(evaluation), now);
            return false;
        }
        return verificationCodeMapper.consumeIfVerifiable(evaluation.record().getId(), now) == 1;
    }

    private String hash(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(messageDigest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String encodePngBase64(BufferedImage image) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", outputStream)) {
                throw new IllegalStateException("CAPTCHA_IMAGE_ENCODE_FAILED");
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("CAPTCHA_IMAGE_ENCODE_FAILED", e);
        }
    }

    private IssuedEmailCode issueEmailCodeInternal(VerificationCodePurpose purpose, String target) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        String code = Integer.toString(ThreadLocalRandom.current().nextInt(100000, 1000000));

        log.info("Generated email code for purpose {}, requestId {}", purpose, code);

        VerificationCodeEntity record = new VerificationCodeEntity(
            null,
            purpose,
            target,
            hash(code),
            VerificationCodeStatus.PENDING_SEND,
            0,
            maxAttempts,
            now.plus(emailCodeTtl),
            now,
            now
        );
        verificationCodeMapper.insert(record);

        Long verificationCodeId = record.getId();
        if (verificationCodeId == null) {
            throw new IllegalStateException("VERIFICATION_CODE_CREATE_FAILED");
        }
        log.info("Created email code request for purpose {}, requestId {}", purpose, verificationCodeId);
        verificationCodeMapper.markOlderVerifiableAsReplaced(purpose.name(), target, verificationCodeId, now);

        boolean sent;
        try {
            mailSenderAdapter.send(target, "verification code", "code=" + code);
            sent = true;
        } catch (RuntimeException ex) {
            sent = false;
            recordEventSafely(new SecurityEvent(
                "VERIFICATION_SEND_FAILED",
                null,
                null,
                null,
                "target=" + target + ",reason=" + ex.getMessage(),
                Instant.now()
            ));
        }
        OffsetDateTime sendResultUpdatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        boolean persistedAsSent = false;
        if (sent) {
            try {
                int updatedRows = verificationCodeMapper.updateSendResultIfPending(
                    verificationCodeId,
                    VerificationCodeStatus.SENT,
                    sendResultUpdatedAt
                );
                persistedAsSent = updatedRows == 1;
                if (!persistedAsSent) {
                    recordEventSafely(new SecurityEvent(
                        "VERIFICATION_SEND_STATUS_UPDATE_FAILED",
                        null,
                        null,
                        null,
                        "requestId=" + verificationCodeId + ",reason=unexpected_status",
                        Instant.now()
                    ));
                    markSendFailedBestEffort(verificationCodeId, sendResultUpdatedAt);
                }
            } catch (RuntimeException ex) {
                log.warn(
                    "Failed to persist SENT status for verification code requestId {}",
                    verificationCodeId,
                    ex
                );
                recordEventSafely(new SecurityEvent(
                    "VERIFICATION_SEND_STATUS_UPDATE_FAILED",
                    null,
                    null,
                    null,
                    "requestId=" + verificationCodeId + ",reason=" + safeReason(ex),
                    Instant.now()
                ));
                markSendFailedBestEffort(verificationCodeId, sendResultUpdatedAt);
            }
        } else {
            markSendFailedBestEffort(verificationCodeId, sendResultUpdatedAt);
        }
        return new IssuedEmailCode(
            code,
            verificationCodeId,
            record.getExpiresAt().toInstant(),
            persistedAsSent
        );
    }

    private String normalizeScenario(String scenario) {
        if (scenario == null) {
            return null;
        }
        String normalizedScenario = scenario.trim().toUpperCase(Locale.ROOT);
        return normalizedScenario.isBlank() ? null : normalizedScenario;
    }

    private record IssuedEmailCode(String code, Long requestId, Instant expiresAt, boolean sent) {
    }

    public record EmailCodeIssueReceipt(String requestId, Instant expiresAt, boolean sent) {
    }

    public record EmailCodeConsumeAttempt(boolean verified, VerificationFailureContext failureContext) {

        public static EmailCodeConsumeAttempt success() {
            return new EmailCodeConsumeAttempt(true, null);
        }

        public static EmailCodeConsumeAttempt failure(VerificationFailureContext failureContext) {
            return new EmailCodeConsumeAttempt(false, failureContext);
        }
    }

    public record VerificationFailureContext(Long recordId, VerificationFailureReason reason) {
    }

    public enum VerificationFailureReason {
        MISSING,
        NOT_VERIFIABLE,
        EXPIRED,
        FROZEN,
        MISMATCH
    }

    private VerificationEvaluation evaluateCode(VerificationCodePurpose purpose, String target, String code) {
        VerificationCodeEntity record = verificationCodeMapper.findLatestByPurposeTarget(purpose.name(), target);
        if (record == null) {
            return new VerificationEvaluation(null, VerificationDecision.MISSING);
        }
        if (record.getStatus() != VerificationCodeStatus.ACTIVE && record.getStatus() != VerificationCodeStatus.SENT) {
            return new VerificationEvaluation(record, VerificationDecision.NOT_VERIFIABLE);
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (record.getExpiresAt().isBefore(now)) {
            return new VerificationEvaluation(record, VerificationDecision.EXPIRED);
        }
        if (record.getAttemptCount() >= record.getMaxAttempts()) {
            return new VerificationEvaluation(record, VerificationDecision.FROZEN);
        }
        if (!record.getCodeHash().equals(hash(code))) {
            return new VerificationEvaluation(record, VerificationDecision.MISMATCH);
        }
        return new VerificationEvaluation(record, VerificationDecision.MATCH);
    }

    private VerificationFailureContext toFailureContext(VerificationEvaluation evaluation) {
        VerificationCodeEntity record = evaluation.record();
        Long recordId = record == null ? null : record.getId();
        VerificationFailureReason reason = switch (evaluation.decision()) {
            case MISSING -> VerificationFailureReason.MISSING;
            case NOT_VERIFIABLE -> VerificationFailureReason.NOT_VERIFIABLE;
            case EXPIRED -> VerificationFailureReason.EXPIRED;
            case FROZEN -> VerificationFailureReason.FROZEN;
            case MISMATCH -> VerificationFailureReason.MISMATCH;
            case MATCH -> VerificationFailureReason.NOT_VERIFIABLE;
        };
        return new VerificationFailureContext(recordId, reason);
    }

    private void applyFailureUpdate(VerificationFailureContext failureContext, OffsetDateTime now) {
        if (failureContext == null || failureContext.recordId() == null) {
            return;
        }
        if (failureContext.reason() == VerificationFailureReason.EXPIRED) {
            verificationCodeMapper.markExpiredIfVerifiable(failureContext.recordId(), now);
            return;
        }
        if (failureContext.reason() == VerificationFailureReason.FROZEN) {
            verificationCodeMapper.markFrozenIfVerifiable(failureContext.recordId(), now);
            return;
        }
        if (failureContext.reason() == VerificationFailureReason.MISMATCH) {
            verificationCodeMapper.incrementAttemptsAndMaybeFreeze(failureContext.recordId(), now);
        }
    }

    private record VerificationEvaluation(VerificationCodeEntity record, VerificationDecision decision) {
    }

    private void markSendFailedBestEffort(Long verificationCodeId, OffsetDateTime updatedAt) {
        try {
            verificationCodeMapper.markSendFailedIfPendingOrReplaced(verificationCodeId, updatedAt);
        } catch (RuntimeException ex) {
            log.warn(
                "Failed to persist SEND_FAILED status for verification code requestId {}",
                verificationCodeId,
                ex
            );
        }
    }

    private void recordEventSafely(SecurityEvent event) {
        try {
            eventRecorder.record(event);
        } catch (RuntimeException ex) {
            log.warn("Failed to record security event {}", event.eventType(), ex);
        }
    }

    private String safeReason(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "unknown";
        }
        return throwable.getMessage();
    }

    private enum VerificationDecision {
        MISSING,
        NOT_VERIFIABLE,
        EXPIRED,
        FROZEN,
        MISMATCH,
        MATCH
    }
}
