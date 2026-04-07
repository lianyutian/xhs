package com.lianyutian.xhs.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.lianyutian.xhs.user.service.event.DatabaseSecurityEventRecorder;
import com.lianyutian.xhs.user.service.event.SecurityEventRecorder;
import com.lianyutian.xhs.user.service.integration.mail.MailSenderAdapter;
import com.lianyutian.xhs.user.service.integration.mail.SpringMailSenderAdapter;
import com.lianyutian.xhs.user.repository.mybatis.SecurityEventMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserAccountMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserProfileMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserRefreshTokenMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserSessionMapper;
import com.lianyutian.xhs.user.repository.mybatis.VerificationCodeMapper;
import com.lianyutian.xhs.user.service.risk.ShortLivedCounterStore;
import java.lang.reflect.Proxy;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import static org.mockito.Mockito.mock;

class AppBeanConfigProductionWiringTest {

    @Test
    void shouldUseProductionAdapters() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(AppBeanConfig.class);
            context.registerBean(SecurityProperties.class, () -> new SecurityProperties(
                new SecurityProperties.Jwt(Duration.ofMinutes(15), Duration.ofDays(7), "issuer", "change-me-change-me-change-me-change-me"),
                new SecurityProperties.Captcha(Duration.ofMinutes(2), Duration.ofMinutes(10), 5)
            ));
            context.registerBean(JavaMailSenderImpl.class, JavaMailSenderImpl::new);
            context.registerBean(SecurityEventMapper.class, () -> stub(SecurityEventMapper.class));
            context.registerBean(VerificationCodeMapper.class, () -> stub(VerificationCodeMapper.class));
            context.registerBean(UserAccountMapper.class, () -> stub(UserAccountMapper.class));
            context.registerBean(UserProfileMapper.class, () -> stub(UserProfileMapper.class));
            context.registerBean(UserSessionMapper.class, () -> stub(UserSessionMapper.class));
            context.registerBean(UserRefreshTokenMapper.class, () -> stub(UserRefreshTokenMapper.class));
            context.registerBean(ShortLivedCounterStore.class, () -> stub(ShortLivedCounterStore.class));
            context.registerBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));
            context.refresh();

            MailSenderAdapter mailSenderAdapter = context.getBean(MailSenderAdapter.class);
            SecurityEventRecorder securityEventRecorder = context.getBean(SecurityEventRecorder.class);

            assertThat(mailSenderAdapter).isInstanceOf(SpringMailSenderAdapter.class);
            assertThat(securityEventRecorder).isInstanceOf(DatabaseSecurityEventRecorder.class);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Class<?> returnType = method.getReturnType();
            if (returnType.equals(boolean.class)) {
                return false;
            }
            if (returnType.equals(int.class) || returnType.equals(long.class) || returnType.equals(short.class) || returnType.equals(byte.class)) {
                return 0;
            }
            if (returnType.equals(float.class) || returnType.equals(double.class)) {
                return 0.0;
            }
            return null;
        });
    }
}
