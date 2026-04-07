package com.lianyutian.xhs.user.service.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultRiskControlServiceUnitTest {

    @Test
    void shouldRespectConfiguredLoginThresholds() {
        DefaultRiskControlService service = new DefaultRiskControlService(
            new InMemoryCounterStore(),
            new RedisKeyHelper("test:user"),
            1,
            2,
            10,
            5,
            Duration.ofMinutes(15),
            Duration.ofMinutes(1),
            Duration.ofMinutes(10),
            8,
            Duration.ofMinutes(5),
            Duration.ofMinutes(1)
        );

        String ip = "1.1.1.1";
        String account = "u@example.com";
        assertThat(service.currentLoginAction(ip, account)).isEqualTo(LoginRiskAction.ALLOW);

        service.recordLoginFailure(ip, account);
        assertThat(service.currentLoginAction(ip, account)).isEqualTo(LoginRiskAction.REQUIRE_CAPTCHA);

        service.recordLoginFailure(ip, account);
        assertThat(service.currentLoginAction(ip, account)).isEqualTo(LoginRiskAction.TEMP_BLOCK);
    }

    @Test
    void shouldRespectConfiguredEmailCodeLimit() {
        DefaultRiskControlService service = new DefaultRiskControlService(
            new InMemoryCounterStore(),
            new RedisKeyHelper("test:user"),
            3,
            6,
            10,
            1,
            Duration.ofMinutes(15),
            Duration.ofMinutes(1),
            Duration.ofMinutes(10),
            8,
            Duration.ofMinutes(5),
            Duration.ofMinutes(1)
        );

        assertThat(service.allowEmailCodeSend("2.2.2.2", "target@example.com")).isTrue();
        assertThat(service.allowEmailCodeSend("2.2.2.2", "target@example.com")).isFalse();
    }

    @Test
    void shouldRespectConfiguredImageCaptchaLimit() {
        DefaultRiskControlService service = new DefaultRiskControlService(
            new InMemoryCounterStore(),
            new RedisKeyHelper("test:user"),
            3,
            6,
            1,
            5,
            Duration.ofMinutes(15),
            Duration.ofMinutes(1),
            Duration.ofMinutes(10),
            8,
            Duration.ofMinutes(5),
            Duration.ofMinutes(1)
        );

        assertThat(service.allowImageCaptchaIssue("4.4.4.4")).isTrue();
        assertThat(service.allowImageCaptchaIssue("4.4.4.4")).isFalse();
    }

    private static final class InMemoryCounterStore implements ShortLivedCounterStore {

        private final Map<String, Long> counters = new HashMap<>();

        @Override
        public long get(String key) {
            return counters.getOrDefault(key, 0L);
        }

        @Override
        public long increment(String key, Duration ttl) {
            long next = counters.getOrDefault(key, 0L) + 1;
            counters.put(key, next);
            return next;
        }

        @Override
        public void delete(Collection<String> keys) {
            for (String key : keys) {
                counters.remove(key);
            }
        }
    }
}
