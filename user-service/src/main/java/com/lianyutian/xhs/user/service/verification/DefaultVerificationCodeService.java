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

    /**
     * 创建图片验证码挑战
     *
     * @param scenario 验证码使用场景（如 LOGIN、REGISTER 等）
     * @return 包含令牌、图片内容的验证码挑战对象
     * @throws IllegalArgumentException 当场景不在支持列表中时抛出异常
     */
    public ImageCaptchaChallenge createImageCaptcha(String scenario) {
        // 标准化场景参数并验证有效性
        String normalizedScenario = scenario == null ? "" : scenario.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_CAPTCHA_SCENARIOS.contains(normalizedScenario)) {
            throw new IllegalArgumentException("INVALID_CAPTCHA_SCENARIO");
        }

        // 生成唯一令牌和验证码文本
        String token = UUID.randomUUID().toString();
        String answer = imageCaptchaProducer.createText();

        log.info("Generated captcha for scenario %s: %s".formatted(normalizedScenario, answer));
        log.info("Captcha token: %s".formatted(token));

        // 生成验证码图片并转换为 Base64 编码
        BufferedImage image = imageCaptchaProducer.createImage(answer);
        String imageContent = "data:image/png;base64," + encodePngBase64(image);

        // 将验证码答案哈希后存储到 Redis，设置过期时间
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

    /**
     * 验证图片验证码答案是否正确
     *
     * @param token 验证码令牌
     * @param answer 用户输入的验证码答案
     * @param expectedScenario 预期的验证码使用场景（可选）
     * @return 验证通过返回 true，否则返回 false
     */
    public boolean verifyImageCaptcha(String token, String answer, String expectedScenario) {
        // 从 Redis 中获取并删除验证码记录（一次性使用）
        String storedValue = redisTemplate.opsForValue().getAndDelete(redisKeyHelper.imageCaptcha(token));
        if (storedValue == null) {
            return false;
        }

        // 解析存储的场景和哈希值
        int separator = storedValue.indexOf(':');
        if (separator <= 0 || separator == storedValue.length() - 1) {
            return false;
        }
        String storedScenario = storedValue.substring(0, separator);
        String storedHash = storedValue.substring(separator + 1);

        // 验证场景是否匹配预期
        String normalizedExpectedScenario = normalizeScenario(expectedScenario);
        if (normalizedExpectedScenario != null && !normalizedExpectedScenario.equals(storedScenario)) {
            return false;
        }

        // 比对答案的哈希值
        return storedHash.equals(hash(answer));
    }

    public String issueEmailCode(VerificationCodePurpose purpose, String target) {
        return issueEmailCodeInternal(purpose, target).code();
    }

    /**
     * 签发邮箱验证码并返回发送回执
     *
     * @param purpose 验证码用途（如注册、登录、找回密码等）
     * @param target 目标邮箱地址
     * @return 包含请求 ID、过期时间和发送状态的验证码签收回执对象
     */
    public EmailCodeIssueReceipt issueEmailCodeWithReceipt(VerificationCodePurpose purpose, String target) {
        // 调用内部方法生成验证码并尝试发送
        IssuedEmailCode issued = issueEmailCodeInternal(purpose, target);

        // 确保请求 ID 不为空，如果缺失则生成新的 UUID
        String requestId = issued.requestId() == null ? UUID.randomUUID().toString() : issued.requestId().toString();
        return new EmailCodeIssueReceipt(requestId, issued.expiresAt(), issued.sent());
    }

    /**
     * 尝试在当前事务中消耗邮箱验证码（验证并标记为已使用）
     *
     * @param purpose 验证码用途（如注册、登录等）
     * @param target 目标邮箱地址
     * @param code 用户输入的验证码
     * @return 验证码消耗尝试结果，包含成功状态或失败上下文信息
     */
    public EmailCodeConsumeAttempt attemptConsumeEmailCodeInCurrentTransaction(VerificationCodePurpose purpose, String target, String code) {
        // 评估验证码的有效性（检查状态、过期时间、匹配性等）
        VerificationEvaluation evaluation = evaluateCode(purpose, target, code);
        if (evaluation.decision() != VerificationDecision.MATCH) {
            return EmailCodeConsumeAttempt.failure(toFailureContext(evaluation));
        }

        // 原子性操作：将验证码标记为 CONSUMED，防止并发重复使用
        boolean consumed = verificationCodeMapper.consumeIfVerifiable(evaluation.record().getId(), OffsetDateTime.now(ZoneOffset.UTC)) == 1;
        if (consumed) {
            return EmailCodeConsumeAttempt.success();
        }

        // 验证码状态已变更，无法再被消耗
        return EmailCodeConsumeAttempt.failure(
            new VerificationFailureContext(evaluation.record().getId(), VerificationFailureReason.NOT_VERIFIABLE)
        );
    }

    /**
     * 记录邮箱验证码验证失败尝试（在新事务中执行，确保独立提交）
     *
     * @param failureContext 包含验证码记录 ID 和失败原因的上下文对象
     */
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

    /**
     * 内部方法：签发邮箱验证码并处理发送结果
     *
     * @param purpose 验证码用途（如注册、登录、找回密码等）
     * @param target 目标邮箱地址
     * @return 已签发的邮箱验证码对象，包含验证码明文、ID、过期时间和发送状态
     * @throws IllegalStateException 当验证码记录创建失败时抛出异常
     */
    private IssuedEmailCode issueEmailCodeInternal(VerificationCodePurpose purpose, String target) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // 生成 6 位随机数字验证码
        String code = Integer.toString(ThreadLocalRandom.current().nextInt(100000, 1000000));

        log.info("Generated email code for purpose {}, requestId {}", purpose, code);

        // 创建验证码记录并持久化到数据库
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

        // 将同一目的和目标的旧验证码标记为 REPLACED，确保唯一有效性
        verificationCodeMapper.markOlderVerifiableAsReplaced(purpose.name(), target, verificationCodeId, now);

        // 发送邮件并捕获异常
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

        // 根据发送结果更新数据库状态
        OffsetDateTime sendResultUpdatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        boolean persistedAsSent = false;
        if (sent) {
            // 尝试将状态更新为 SENT，如果失败则标记为 SEND_FAILED
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
            // 发送失败，标记为 SEND_FAILED
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

    /**
     * 评估验证码的有效性，按顺序检查多个维度
     *
     * @param purpose 验证码用途（如注册、登录等）
     * @param target 目标邮箱地址
     * @param code 用户输入的验证码
     * @return 包含验证码记录和验证决策的评估结果对象
     */
    private VerificationEvaluation evaluateCode(VerificationCodePurpose purpose, String target, String code) {
        // 查询最新的验证码记录
        VerificationCodeEntity record = verificationCodeMapper.findLatestByPurposeTarget(purpose.name(), target);
        if (record == null) {
            return new VerificationEvaluation(null, VerificationDecision.MISSING);
        }

        // 检查验证码状态是否可验证（ACTIVE 或 SENT）
        if (record.getStatus() != VerificationCodeStatus.ACTIVE && record.getStatus() != VerificationCodeStatus.SENT) {
            return new VerificationEvaluation(record, VerificationDecision.NOT_VERIFIABLE);
        }

        // 检查验证码是否已过期
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (record.getExpiresAt().isBefore(now)) {
            return new VerificationEvaluation(record, VerificationDecision.EXPIRED);
        }

        // 检查尝试次数是否已达上限（冻结）
        if (record.getAttemptCount() >= record.getMaxAttempts()) {
            return new VerificationEvaluation(record, VerificationDecision.FROZEN);
        }

        // 比对验证码哈希值
        if (!record.getCodeHash().equals(hash(code))) {
            return new VerificationEvaluation(record, VerificationDecision.MISMATCH);
        }

        // 所有检查通过，验证码有效
        return new VerificationEvaluation(record, VerificationDecision.MATCH);
    }

    /**
     * 将验证码评估结果转换为失败上下文信息
     *
     * @param evaluation 验证码评估结果对象
     * @return 包含记录 ID 和失败原因的上下文对象
     */
    private VerificationFailureContext toFailureContext(VerificationEvaluation evaluation) {
        VerificationCodeEntity record = evaluation.record();
        Long recordId = record == null ? null : record.getId();

        // 根据验证决策映射对应的失败原因
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

    /**
     * 根据失败原因应用相应的数据库更新操作
     *
     * @param failureContext 包含验证码记录 ID 和失败原因的上下文对象
     * @param now 当前时间戳
     */
    private void applyFailureUpdate(VerificationFailureContext failureContext, OffsetDateTime now) {
        // 忽略无效的失败上下文
        if (failureContext == null || failureContext.recordId() == null) {
            return;
        }

        // 根据失败原因执行不同的状态更新
        if (failureContext.reason() == VerificationFailureReason.EXPIRED) {
            verificationCodeMapper.markExpiredIfVerifiable(failureContext.recordId(), now);
            return;
        }
        if (failureContext.reason() == VerificationFailureReason.FROZEN) {
            verificationCodeMapper.markFrozenIfVerifiable(failureContext.recordId(), now);
            return;
        }

        // 验证码不匹配：增加尝试次数，达到上限则自动冻结
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

    /**
     * 验证码验证决策枚举，表示验证码评估的最终结果
     */
    private enum VerificationDecision {
        /** 验证码不存在或为空 */
        MISSING,
        /** 验证码状态不可验证（如已使用、已替换等） */
        NOT_VERIFIABLE,
        /** 验证码已过期 */
        EXPIRED,
        /** 验证码已被冻结（异常锁定） */
        FROZEN,
        /** 验证码答案不匹配 */
        MISMATCH,
        /** 验证码验证通过 */
        MATCH
    }
}
