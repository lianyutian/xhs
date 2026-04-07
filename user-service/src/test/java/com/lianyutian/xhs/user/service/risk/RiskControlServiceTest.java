package com.lianyutian.xhs.user.service.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.lianyutian.xhs.user.support.AbstractDbIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RiskControlServiceTest extends AbstractDbIntegrationTest {

    @Autowired
    private DefaultRiskControlService service;

    @Test
    void shouldEscalateLoginProtectionByFailureCount() {
        String ip = "1.1.1.1";
        String account = "a@example.com";
        assertThat(service.currentLoginAction(ip, account)).isEqualTo(LoginRiskAction.ALLOW);

        service.recordLoginFailure(ip, account);
        service.recordLoginFailure(ip, account);
        service.recordLoginFailure(ip, account);
        assertThat(service.currentLoginAction(ip, account)).isEqualTo(LoginRiskAction.REQUIRE_CAPTCHA);

        service.recordLoginFailure(ip, account);
        service.recordLoginFailure(ip, account);
        service.recordLoginFailure(ip, account);
        assertThat(service.currentLoginAction(ip, account)).isEqualTo(LoginRiskAction.TEMP_BLOCK);
    }

    @Test
    void shouldApplyIndependentIpAndAccountDimensions() {
        for (int i = 0; i < 3; i++) {
            service.recordLoginFailure("2.2.2.2", "x@example.com");
        }
        assertThat(service.currentLoginAction("2.2.2.2", "other@example.com"))
            .isEqualTo(LoginRiskAction.REQUIRE_CAPTCHA);
    }

    @Test
    void shouldClearAllDimensionsWhenResetAfterSuccessfulLogin() {
        for (int i = 0; i < 3; i++) {
            service.recordLoginFailure("3.3.3.3", "a@example.com");
        }
        service.resetLoginFailures("3.3.3.3", "a@example.com");
        assertThat(service.currentLoginAction("3.3.3.3", "b@example.com"))
            .isEqualTo(LoginRiskAction.ALLOW);
    }

    @Test
    void shouldRateLimitEmailCodeByIpTargetAndCombinedDimensions() {
        String ip = "6.6.6.6";
        String target = "rate@example.com";
        for (int i = 0; i < 5; i++) {
            assertThat(service.allowEmailCodeSend(ip, target)).isTrue();
        }
        assertThat(service.allowEmailCodeSend(ip, target)).isFalse();
        assertThat(service.allowEmailCodeSend(ip, "other@example.com")).isFalse();
        assertThat(service.allowEmailCodeSend("7.7.7.7", target)).isFalse();
    }

    @Test
    void shouldRateLimitRefreshBySourceAndTokenDimensions() {
        String ip = "9.9.9.9";
        String tokenHash = "token-hash";
        for (int i = 0; i < 8; i++) {
            assertThat(service.allowRefresh(ip, tokenHash)).isTrue();
        }
        assertThat(service.allowRefresh(ip, tokenHash)).isFalse();
    }

    @Test
    void shouldRateLimitImageCaptchaBySourceIp() {
        String ip = "4.4.4.4";
        for (int i = 0; i < 10; i++) {
            assertThat(service.allowImageCaptchaIssue(ip)).isTrue();
        }
        assertThat(service.allowImageCaptchaIssue(ip)).isFalse();
        assertThat(service.allowImageCaptchaIssue("5.5.5.5")).isTrue();
    }
}
