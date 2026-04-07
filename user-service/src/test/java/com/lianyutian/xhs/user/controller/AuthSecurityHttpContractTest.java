package com.lianyutian.xhs.user.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lianyutian.xhs.user.model.domain.VerificationCodePurpose;
import com.lianyutian.xhs.user.service.auth.AuthTokens;
import com.lianyutian.xhs.user.service.auth.DefaultAuthService;
import com.lianyutian.xhs.user.service.verification.DefaultVerificationCodeService;
import com.lianyutian.xhs.user.support.AbstractDbIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

class AuthSecurityHttpContractTest extends AbstractDbIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private DefaultVerificationCodeService verificationCodeService;

    @Autowired
    private DefaultAuthService authService;

    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();
    }

    @Test
    void shouldRejectMeRequestWhenTokenMissing() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("unauthorized"));
    }

    @Test
    void shouldRejectMeRequestWhenTokenInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("invalid access token"));
    }

    @Test
    void shouldReturnUnifiedInvalidCredentialsCode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "email": "missing@example.com",
                      "password": "bad-pass",
                      "userAgent": "test-agent"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void shouldRequireImageCaptchaWhenIssuingEmailCode() throws Exception {
        mockMvc.perform(post("/api/v1/verification/email-code")
                .with(remoteAddr("8.8.8.8"))
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "purpose": "REGISTER",
                      "targetEmail": "need-captcha@example.com"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("CAPTCHA_REQUIRED"));
    }

    @Test
    void shouldNotExposeRawInternalMessageForInvalidPurpose() throws Exception {
        mockMvc.perform(post("/api/v1/verification/email-code")
                .with(remoteAddr("8.8.8.8"))
                .contentType(APPLICATION_JSON)
                .content(emailCodeRequestBody("BAD_PURPOSE", "u@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldIgnoreXffWhenProxyNotTrustedForRateLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/verification/email-code")
                    .with(remoteAddr("9.9.9.9"))
                    .header("X-Forwarded-For", "1.1.1." + i)
                    .contentType(APPLICATION_JSON)
                    .content(emailCodeRequestBody("REGISTER", "u%d@example.com".formatted(i))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
        }

        mockMvc.perform(post("/api/v1/verification/email-code")
                .with(remoteAddr("9.9.9.9"))
                .header("X-Forwarded-For", "8.8.8.8")
                .contentType(APPLICATION_JSON)
                .content(emailCodeRequestBody("REGISTER", "blocked@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("EMAIL_CODE_RATE_LIMITED"));
    }

    @Test
    void shouldRateLimitImageCaptchaBySourceIp() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/v1/verification/image-captcha")
                    .with(remoteAddr("4.4.4.4"))
                    .queryParam("scenario", "LOGIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
        }

        mockMvc.perform(get("/api/v1/verification/image-captcha")
                .with(remoteAddr("4.4.4.4"))
                .queryParam("scenario", "LOGIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("IMAGE_CAPTCHA_RATE_LIMITED"));
    }

    @Test
    void shouldAllowMeRequestWithValidToken() throws Exception {
        String accessToken = issueAccessToken("http-me@example.com");
        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.email").value("http-me@example.com"));
    }

    @Test
    void shouldMaskDuplicateRegisterAsInvalidRegisterCode() throws Exception {
        String email = "duplicate-register@example.com";
        String firstCode = verificationCodeService.issueEmailCode(VerificationCodePurpose.REGISTER, email);
        authService.register(email, "Password123!", firstCode);
        String secondCode = verificationCodeService.issueEmailCode(VerificationCodePurpose.REGISTER, email);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "Password123!",
                      "verificationCode": "%s"
                    }
                    """.formatted(email, secondCode)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("INVALID_REGISTER_CODE"));
    }

    private String issueAccessToken(String email) {
        String code = verificationCodeService.issueEmailCode(VerificationCodePurpose.REGISTER, email);
        authService.register(email, "Password123!", code);
        AuthTokens tokens = authService.login(email, "Password123!", "6.6.6.6", "agent");
        return tokens.accessToken();
    }

    private String emailCodeRequestBody(String purpose, String targetEmail) {
        var challenge = verificationCodeService.createImageCaptcha("REGISTER");
        return """
            {
              "purpose": "%s",
              "targetEmail": "%s",
              "captchaToken": "%s",
              "captchaAnswer": "%s"
            }
            """.formatted(purpose, targetEmail, challenge.token(), challenge.answer());
    }

    private RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
