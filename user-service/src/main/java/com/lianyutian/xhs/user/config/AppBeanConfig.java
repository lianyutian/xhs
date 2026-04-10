package com.lianyutian.xhs.user.config;

import com.google.code.kaptcha.Producer;
import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.google.code.kaptcha.util.Config;
import com.lianyutian.xhs.user.controller.SourceIpResolver;
import com.lianyutian.xhs.user.service.auth.DefaultAuthService;
import com.lianyutian.xhs.user.service.event.DatabaseSecurityEventRecorder;
import com.lianyutian.xhs.user.service.event.SecurityEventRecorder;
import com.lianyutian.xhs.user.service.integration.mail.MailSenderAdapter;
import com.lianyutian.xhs.user.service.integration.mail.SpringMailSenderAdapter;
import com.lianyutian.xhs.user.repository.mybatis.SecurityEventMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserAccountMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserAddressMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserProfileMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserRefreshTokenMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserSessionMapper;
import com.lianyutian.xhs.user.repository.mybatis.VerificationCodeMapper;
import com.lianyutian.xhs.user.service.address.DefaultAddressService;
import com.lianyutian.xhs.user.service.risk.DefaultRiskControlService;
import com.lianyutian.xhs.user.service.risk.ProtectedWriteGuard;
import com.lianyutian.xhs.user.service.risk.RedisKeyHelper;
import com.lianyutian.xhs.user.service.risk.RedisShortLivedCounterStore;
import com.lianyutian.xhs.user.service.risk.ShortLivedCounterStore;
import com.lianyutian.xhs.user.service.verification.DefaultVerificationCodeService;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppBeanConfig {

    @Bean
    public SecurityEventRecorder securityEventRecorder(SecurityEventMapper securityEventMapper) {
        return new DatabaseSecurityEventRecorder(securityEventMapper);
    }

    @Bean
    public MailSenderAdapter mailSenderAdapter(JavaMailSender javaMailSender, @Value("${spring.mail.username:}") String from) {
        return new SpringMailSenderAdapter(javaMailSender, from);
    }

    @Bean
    public RedisKeyHelper redisKeyHelper() {
        return new RedisKeyHelper("xhs:user");
    }

    @Bean
    public SourceIpResolver sourceIpResolver(@Value("${app.security.trusted-proxies:}") String trustedProxiesRaw) {
        Set<String> trustedProxies = Arrays.stream(trustedProxiesRaw.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toSet());
        return new SourceIpResolver(trustedProxies);
    }

    @Bean
    @ConditionalOnMissingBean(ShortLivedCounterStore.class)
    public ShortLivedCounterStore shortLivedCounterStore(StringRedisTemplate redisTemplate) {
        return new RedisShortLivedCounterStore(redisTemplate);
    }

    @Bean
    public DefaultRiskControlService defaultRiskControlService(
        ShortLivedCounterStore counterStore,
        RedisKeyHelper redisKeyHelper,
        @Value("${app.security.risk.login.captcha-threshold:3}") int captchaThreshold,
        @Value("${app.security.risk.login.temp-block-threshold:6}") int tempBlockThreshold,
        @Value("${app.security.risk.image-captcha.limit:10}") int imageCaptchaLimit,
        @Value("${app.security.risk.email-code.limit:5}") int emailCodeLimit,
        @Value("${app.security.risk.login.failure-ttl:PT15M}") String loginFailureTtlRaw,
        @Value("${app.security.risk.image-captcha.ttl:PT1M}") String imageCaptchaTtlRaw,
        @Value("${app.security.risk.email-code.ttl:PT10M}") String emailCodeTtlRaw,
        @Value("${app.security.risk.refresh.limit:8}") int refreshLimit,
        @Value("${app.security.risk.refresh.ttl:PT5M}") String refreshTtlRaw,
        @Value("${app.security.risk.upload.ttl:PT1M}") String uploadTtlRaw
    ) {
        return new DefaultRiskControlService(
            counterStore,
            redisKeyHelper,
            captchaThreshold,
            tempBlockThreshold,
            imageCaptchaLimit,
            emailCodeLimit,
            parseDuration(loginFailureTtlRaw),
            parseDuration(imageCaptchaTtlRaw),
            parseDuration(emailCodeTtlRaw),
            refreshLimit,
            parseDuration(refreshTtlRaw),
            parseDuration(uploadTtlRaw)
        );
    }

    @Bean
    public ProtectedWriteGuard protectedWriteGuard(
        SecurityEventRecorder securityEventRecorder,
        DefaultRiskControlService riskControlService,
        @Value("${app.security.risk.upload.limit:10}") int uploadRateLimit,
        @Value("${app.security.risk.upload.max-bytes:5242880}") long maxUploadBytes
    ) {
        return new ProtectedWriteGuard(securityEventRecorder, riskControlService, uploadRateLimit, maxUploadBytes);
    }

    private Duration parseDuration(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("duration value must not be blank");
        }
        return Duration.parse(value);
    }

    @Bean
    public Producer imageCaptchaProducer() {
        Properties properties = new Properties();
        properties.setProperty("kaptcha.border", "no");
        properties.setProperty("kaptcha.image.width", "130");
        properties.setProperty("kaptcha.image.height", "48");
        properties.setProperty("kaptcha.textproducer.char.length", "4");
        properties.setProperty("kaptcha.textproducer.char.string", "23456789");
        properties.setProperty("kaptcha.textproducer.font.size", "32");
        properties.setProperty("kaptcha.textproducer.char.space", "6");
        properties.setProperty("kaptcha.textproducer.font.color", "31,41,55");
        DefaultKaptcha kaptcha = new DefaultKaptcha();
        kaptcha.setConfig(new Config(properties));
        return kaptcha;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DefaultVerificationCodeService defaultVerificationCodeService(
        MailSenderAdapter mailSenderAdapter,
        SecurityEventRecorder securityEventRecorder,
        SecurityProperties securityProperties,
        StringRedisTemplate redisTemplate,
        RedisKeyHelper redisKeyHelper,
        VerificationCodeMapper verificationCodeMapper,
        Producer imageCaptchaProducer
    ) {
        return new DefaultVerificationCodeService(
            mailSenderAdapter,
            securityProperties.captcha().maxAttempts(),
            securityEventRecorder,
            securityProperties.captcha().imageTtl(),
            securityProperties.captcha().emailTtl(),
            redisTemplate,
            redisKeyHelper,
            verificationCodeMapper,
            imageCaptchaProducer
        );
    }

    @Bean
    public DefaultAuthService defaultAuthService(
        DefaultVerificationCodeService verificationCodeService,
        DefaultRiskControlService riskControlService,
        SecurityEventRecorder securityEventRecorder,
        SecurityProperties securityProperties,
        UserAccountMapper userAccountMapper,
        UserProfileMapper userProfileMapper,
        UserSessionMapper userSessionMapper,
        UserRefreshTokenMapper userRefreshTokenMapper,
        PasswordEncoder passwordEncoder
    ) {
        return new DefaultAuthService(
            verificationCodeService,
            riskControlService,
            securityEventRecorder,
            securityProperties.jwt().accessTokenTtl(),
            securityProperties.jwt().refreshTokenTtl(),
            securityProperties.jwt().issuer(),
            securityProperties.jwt().secret(),
            userAccountMapper,
            userProfileMapper,
            userSessionMapper,
            userRefreshTokenMapper,
            passwordEncoder
        );
    }

    @Bean
    public DefaultAddressService defaultAddressService(UserAccountMapper userAccountMapper, UserAddressMapper userAddressMapper) {
        return new DefaultAddressService(userAccountMapper, userAddressMapper);
    }
}
