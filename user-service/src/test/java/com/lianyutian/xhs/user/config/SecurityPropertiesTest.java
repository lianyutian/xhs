package com.lianyutian.xhs.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SecurityPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class)
        .withPropertyValues(
            "app.security.jwt.access-token-ttl=PT15M",
            "app.security.jwt.refresh-token-ttl=P7D",
            "app.security.jwt.issuer=user-service",
            "app.security.jwt.secret=test-secret-at-least-32-characters-long",
            "app.security.captcha.image-ttl=PT2M",
            "app.security.captcha.email-ttl=PT10M",
            "app.security.captcha.max-attempts=5",
            "spring.data.redis.host=localhost",
            "spring.mail.host=localhost"
        );

    @Test
    void shouldBindSecurityPropertiesFromEnvironment() {
        contextRunner.run(context -> {
            SecurityProperties properties = context.getBean(SecurityProperties.class);
            assertThat(properties.jwt().issuer()).isEqualTo("user-service");
            assertThat(properties.captcha().maxAttempts()).isEqualTo(5);
        });
    }

    @Test
    void shouldFailWhenJwtSecretTooShort() {
        contextRunner.withPropertyValues(
            "app.security.jwt.secret=short"
        ).run(context -> {
            assertThat(context.getStartupFailure()).isNotNull();
        });
    }

    @Test
    void shouldFailWhenJwtSecretUsesPlaceholderValue() {
        contextRunner.withPropertyValues(
            "app.security.jwt.secret=replace-with-at-least-32-char-secret"
        ).run(context -> {
            assertThat(context.getStartupFailure()).isNotNull();
        });
    }

    @EnableConfigurationProperties(SecurityProperties.class)
    static class TestConfig {
    }
}
