package com.lianyutian.xhs.user.service.risk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RedisKeyHelperTest {

    @Test
    void shouldBuildRiskAndCaptchaKeysWithStableConvention() {
        RedisKeyHelper helper = new RedisKeyHelper("xhs:user");

        assertThat(helper.imageCaptcha("token-1")).isEqualTo("xhs:user:captcha:image:token-1");
        assertThat(helper.emailCaptcha("REGISTER", "a@example.com")).isEqualTo("xhs:user:captcha:email:REGISTER:a@example.com");
        assertThat(helper.captchaProof("proof-1")).isEqualTo("xhs:user:captcha:proof:proof-1");
        assertThat(helper.rateLimit("login", "ip", "1.1.1.1")).isEqualTo("xhs:user:risk:rate:login:ip:1.1.1.1");
        assertThat(helper.failureCounter("login", "account", "a@example.com")).isEqualTo("xhs:user:risk:fail:login:account:a@example.com");
    }
}
