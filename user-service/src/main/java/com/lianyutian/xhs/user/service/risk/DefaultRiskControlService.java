package com.lianyutian.xhs.user.service.risk;

import java.time.Duration;
import java.util.List;

public class DefaultRiskControlService implements RiskControlService {

    private static final int DEFAULT_CAPTCHA_THRESHOLD = 3;
    private static final int DEFAULT_TEMP_BLOCK_THRESHOLD = 6;
    private static final int DEFAULT_IMAGE_CAPTCHA_LIMIT = 10;
    private static final int DEFAULT_EMAIL_CODE_LIMIT = 5;
    private static final int DEFAULT_REFRESH_LIMIT = 8;
    private static final Duration DEFAULT_LOGIN_FAILURE_TTL = Duration.ofMinutes(15);
    private static final Duration DEFAULT_IMAGE_CAPTCHA_TTL = Duration.ofMinutes(1);
    private static final Duration DEFAULT_EMAIL_CODE_TTL = Duration.ofMinutes(10);
    private static final Duration DEFAULT_REFRESH_TTL = Duration.ofMinutes(5);
    private static final Duration DEFAULT_UPLOAD_TTL = Duration.ofMinutes(1);

    private final ShortLivedCounterStore counterStore;
    private final RedisKeyHelper redisKeyHelper;
    private final int captchaThreshold;
    private final int tempBlockThreshold;
    private final int imageCaptchaLimit;
    private final int emailCodeLimit;
    private final int refreshLimit;
    private final Duration loginFailureTtl;
    private final Duration imageCaptchaTtl;
    private final Duration emailCodeTtl;
    private final Duration refreshTtl;
    private final Duration uploadTtl;

    public DefaultRiskControlService(ShortLivedCounterStore counterStore, RedisKeyHelper redisKeyHelper) {
        this(
            counterStore,
            redisKeyHelper,
            DEFAULT_CAPTCHA_THRESHOLD,
            DEFAULT_TEMP_BLOCK_THRESHOLD,
            DEFAULT_IMAGE_CAPTCHA_LIMIT,
            DEFAULT_EMAIL_CODE_LIMIT,
            DEFAULT_LOGIN_FAILURE_TTL,
            DEFAULT_IMAGE_CAPTCHA_TTL,
            DEFAULT_EMAIL_CODE_TTL,
            DEFAULT_REFRESH_LIMIT,
            DEFAULT_REFRESH_TTL,
            DEFAULT_UPLOAD_TTL
        );
    }

    public DefaultRiskControlService(
        ShortLivedCounterStore counterStore,
        RedisKeyHelper redisKeyHelper,
        int captchaThreshold,
        int tempBlockThreshold,
        int imageCaptchaLimit,
        int emailCodeLimit,
        Duration loginFailureTtl,
        Duration imageCaptchaTtl,
        Duration emailCodeTtl,
        int refreshLimit,
        Duration refreshTtl,
        Duration uploadTtl
    ) {
        this.counterStore = counterStore;
        this.redisKeyHelper = redisKeyHelper;
        this.captchaThreshold = captchaThreshold;
        this.tempBlockThreshold = tempBlockThreshold;
        this.imageCaptchaLimit = imageCaptchaLimit;
        this.emailCodeLimit = emailCodeLimit;
        this.refreshLimit = refreshLimit;
        this.loginFailureTtl = loginFailureTtl;
        this.imageCaptchaTtl = imageCaptchaTtl;
        this.emailCodeTtl = emailCodeTtl;
        this.refreshTtl = refreshTtl;
        this.uploadTtl = uploadTtl;
    }

    /**
     * 评估当前登录请求的风险等级并决定应采取的行动
     *
     * @param sourceIp 请求来源 IP 地址
     * @param account 登录账号
     * @return 登录风险行动决策：允许登录、要求验证码或临时封锁
     */
    @Override
    public LoginRiskAction currentLoginAction(String sourceIp, String account) {
        // 综合计算三种维度的失败次数：IP+账号组合、仅IP、仅账号，取最大值作为风险评估依据
        long failures = Math.max(
            counterValue(loginFailureCombinedKey(sourceIp, account)),
            Math.max(counterValue(loginFailureIpKey(sourceIp)), counterValue(loginFailureAccountKey(account)))
        );

        // 失败次数达到临时封锁阈值，禁止登录
        if (failures >= tempBlockThreshold) {
            return LoginRiskAction.TEMP_BLOCK;
        }

        // 失败次数达到验证码阈值，要求人机验证
        if (failures >= captchaThreshold) {
            return LoginRiskAction.REQUIRE_CAPTCHA;
        }

        // 风险较低，允许正常登录
        return LoginRiskAction.ALLOW;
    }

    @Override
    public void recordLoginFailure(String sourceIp, String account) {
        increment(loginFailureIpKey(sourceIp), loginFailureTtl);
        increment(loginFailureAccountKey(account), loginFailureTtl);
        increment(loginFailureCombinedKey(sourceIp, account), loginFailureTtl);
    }

    @Override
    public void resetLoginFailures(String sourceIp, String account) {
        counterStore.delete(List.of(
            loginFailureIpKey(sourceIp),
            loginFailureAccountKey(account),
            loginFailureCombinedKey(sourceIp, account)
        ));
    }

    @Override
    public int incrementUploadCount(Long userId) {
        return (int) increment(uploadKey(userId), uploadTtl);
    }

    @Override
    public boolean allowImageCaptchaIssue(String sourceIp) {
        long ip = increment(imageCaptchaIpKey(sourceIp), imageCaptchaTtl);
        return ip <= imageCaptchaLimit;
    }

    @Override
    public boolean allowEmailCodeSend(String sourceIp, String targetIdentifier) {
        long ip = increment(emailCodeIpKey(sourceIp), emailCodeTtl);
        long target = increment(emailCodeTargetKey(targetIdentifier), emailCodeTtl);
        long combined = increment(emailCodeCombinedKey(sourceIp, targetIdentifier), emailCodeTtl);
        return ip <= emailCodeLimit && target <= emailCodeLimit && combined <= emailCodeLimit;
    }

    @Override
    public boolean allowRefresh(String sourceIp, String refreshTokenHash) {
        long ip = increment(refreshIpKey(sourceIp), refreshTtl);
        long token = increment(refreshTokenKey(refreshTokenHash), refreshTtl);
        long combined = increment(refreshCombinedKey(sourceIp, refreshTokenHash), refreshTtl);
        return ip <= refreshLimit && token <= refreshLimit && combined <= refreshLimit;
    }

    private long counterValue(String key) {
        return counterStore.get(key);
    }

    private long increment(String key, Duration ttl) {
        return counterStore.increment(key, ttl);
    }

    private String loginFailureIpKey(String sourceIp) {
        return redisKeyHelper.failureCounter("login", "ip", sourceIp);
    }

    private String loginFailureAccountKey(String account) {
        return redisKeyHelper.failureCounter("login", "account", account);
    }

    private String loginFailureCombinedKey(String sourceIp, String account) {
        return redisKeyHelper.failureCounter("login", "ip_account", sourceIp + "|" + account);
    }

    private String uploadKey(Long userId) {
        return redisKeyHelper.rateLimit("upload", "user", String.valueOf(userId));
    }

    private String imageCaptchaIpKey(String sourceIp) {
        return redisKeyHelper.rateLimit("image_captcha_issue", "ip", sourceIp);
    }

    private String emailCodeIpKey(String sourceIp) {
        return redisKeyHelper.rateLimit("email_code_send", "ip", sourceIp);
    }

    private String emailCodeTargetKey(String targetIdentifier) {
        return redisKeyHelper.rateLimit("email_code_send", "target", targetIdentifier);
    }

    private String emailCodeCombinedKey(String sourceIp, String targetIdentifier) {
        return redisKeyHelper.rateLimit("email_code_send", "ip_target", sourceIp + "|" + targetIdentifier);
    }

    private String refreshIpKey(String sourceIp) {
        return redisKeyHelper.rateLimit("refresh", "ip", sourceIp);
    }

    private String refreshTokenKey(String refreshTokenHash) {
        return redisKeyHelper.rateLimit("refresh", "token", refreshTokenHash);
    }

    private String refreshCombinedKey(String sourceIp, String refreshTokenHash) {
        return redisKeyHelper.rateLimit("refresh", "ip_token", sourceIp + "|" + refreshTokenHash);
    }
}
