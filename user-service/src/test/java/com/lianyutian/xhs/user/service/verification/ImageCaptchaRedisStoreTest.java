package com.lianyutian.xhs.user.service.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.code.kaptcha.Producer;
import com.lianyutian.xhs.user.service.event.SecurityEventRecorder;
import com.lianyutian.xhs.user.repository.mybatis.VerificationCodeMapper;
import com.lianyutian.xhs.user.service.risk.RedisKeyHelper;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class ImageCaptchaRedisStoreTest {

    @SuppressWarnings("unchecked")
    @Test
    void shouldStoreAndConsumeImageCaptchaFromRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Producer imageCaptchaProducer = mock(Producer.class);
        when(imageCaptchaProducer.createText()).thenReturn("1234");
        when(imageCaptchaProducer.createImage("1234")).thenReturn(new BufferedImage(130, 48, BufferedImage.TYPE_INT_RGB));

        DefaultVerificationCodeService service = new DefaultVerificationCodeService(
            (to, subject, content) -> {
            },
            5,
            mock(SecurityEventRecorder.class),
            Duration.ofMinutes(2),
            Duration.ofMinutes(10),
            redisTemplate,
            new RedisKeyHelper("xhs:user"),
            mock(VerificationCodeMapper.class),
            imageCaptchaProducer
        );

        ImageCaptchaChallenge challenge = service.createImageCaptcha("LOGIN");
        verify(valueOperations).set(
            eq("xhs:user:captcha:image:" + challenge.token()),
            anyString(),
            eq(Duration.ofMinutes(2))
        );

        when(valueOperations.getAndDelete("xhs:user:captcha:image:" + challenge.token()))
            .thenReturn("LOGIN:" + hash(challenge.answer()))
            .thenReturn(null);

        assertThat(service.verifyImageCaptcha(challenge.token(), challenge.answer())).isTrue();
        assertThat(service.verifyImageCaptcha(challenge.token(), challenge.answer())).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldRejectImageCaptchaWhenAnswerMismatch() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(any())).thenReturn("LOGIN:" + hash("1234"));
        Producer imageCaptchaProducer = mock(Producer.class);

        DefaultVerificationCodeService service = new DefaultVerificationCodeService(
            (to, subject, content) -> {
            },
            5,
            mock(SecurityEventRecorder.class),
            Duration.ofMinutes(2),
            Duration.ofMinutes(10),
            redisTemplate,
            new RedisKeyHelper("xhs:user"),
            mock(VerificationCodeMapper.class),
            imageCaptchaProducer
        );

        assertThat(service.verifyImageCaptcha("token-1", "9999")).isFalse();
    }

    private String hash(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(messageDigest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
