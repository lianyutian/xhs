package com.lianyutian.xhs.user.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lianyutian.xhs.user.repository.mybatis.UserAccountMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserProfileMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserRefreshTokenMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserSessionMapper;
import com.lianyutian.xhs.user.service.event.SecurityEventRecorder;
import com.lianyutian.xhs.user.service.risk.LoginRiskAction;
import com.lianyutian.xhs.user.service.risk.RiskControlService;
import com.lianyutian.xhs.user.service.verification.DefaultVerificationCodeService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class DefaultAuthServiceUnitTest {

    @Test
    void shouldStillVerifyPasswordWhenAccountDoesNotExist() {
        DefaultVerificationCodeService verificationCodeService = mock(DefaultVerificationCodeService.class);
        RiskControlService riskControlService = mock(RiskControlService.class);
        SecurityEventRecorder securityEventRecorder = mock(SecurityEventRecorder.class);
        UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
        UserProfileMapper userProfileMapper = mock(UserProfileMapper.class);
        UserSessionMapper userSessionMapper = mock(UserSessionMapper.class);
        UserRefreshTokenMapper userRefreshTokenMapper = mock(UserRefreshTokenMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        when(riskControlService.currentLoginAction(anyString(), anyString())).thenReturn(LoginRiskAction.ALLOW);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        DefaultAuthService authService = new DefaultAuthService(
            verificationCodeService,
            riskControlService,
            securityEventRecorder,
            Duration.ofMinutes(15),
            Duration.ofDays(7),
            "test-issuer",
            "test-secret-at-least-32-characters-long",
            userAccountMapper,
            userProfileMapper,
            userSessionMapper,
            userRefreshTokenMapper,
            passwordEncoder
        );

        AuthTokens result = authService.login("missing@example.com", "Password123!", "1.1.1.1", "agent");

        assertThat(result.errorCode()).isEqualTo("INVALID_CREDENTIALS");
        verify(passwordEncoder).matches(eq("Password123!"), anyString());
    }
}
