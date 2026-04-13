package com.lianyutian.xhs.user.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    void shouldAllowCorsPreflightForProtectedEndpoint() throws Exception {
        mockMvc.perform(options("/api/v1/auth/me")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
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
    void shouldRejectAddressEndpointsWhenTokenMissing() throws Exception {
        mockMvc.perform(get("/api/v1/addresses"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("unauthorized"));
    }

    @Test
    void shouldAllowCreateAddressAndReturnFullAddressWhenAuthenticated() throws Exception {
        String accessToken = issueAccessToken("http-address@example.com");

        mockMvc.perform(post("/api/v1/addresses")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "recipientName": "name",
                      "recipientPhone": "13800000000",
                      "province": "zhejiang",
                      "city": "hangzhou",
                      "district": "xihu",
                      "detailAddress": "xihu road 1",
                      "postalCode": "310000",
                      "defaultAddress": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.addressId").isNumber())
            .andExpect(jsonPath("$.data.recipientName").value("name"))
            .andExpect(jsonPath("$.data.recipientPhone").value("13800000000"))
            .andExpect(jsonPath("$.data.province").value("zhejiang"))
            .andExpect(jsonPath("$.data.city").value("hangzhou"))
            .andExpect(jsonPath("$.data.district").value("xihu"))
            .andExpect(jsonPath("$.data.detailAddress").value("xihu road 1"))
            .andExpect(jsonPath("$.data.postalCode").value("310000"))
            .andExpect(jsonPath("$.data.defaultAddress").value(true))
            .andExpect(jsonPath("$.data.createdAt").isString())
            .andExpect(jsonPath("$.data.updatedAt").isString());
    }

    @Test
    void shouldSupportUpdateAndDeleteAddressViaHttp() throws Exception {
        String accessToken = issueAccessToken("http-address-update-delete@example.com");
        long addressId = createAddressAndReturnId(accessToken, "xihu road update target");

        mockMvc.perform(put("/api/v1/addresses/{addressId}", addressId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "recipientName": "updated-name",
                      "recipientPhone": "13900000000",
                      "province": "jiangsu",
                      "city": "nanjing",
                      "district": "gulou",
                      "detailAddress": "gulou road 9",
                      "postalCode": "210000",
                      "defaultAddress": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.addressId").value(addressId))
            .andExpect(jsonPath("$.data.recipientName").value("updated-name"))
            .andExpect(jsonPath("$.data.recipientPhone").value("13900000000"))
            .andExpect(jsonPath("$.data.detailAddress").value("gulou road 9"))
            .andExpect(jsonPath("$.data.defaultAddress").value(true));

        mockMvc.perform(delete("/api/v1/addresses/{addressId}", addressId)
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(get("/api/v1/addresses")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void shouldRejectAddressPutAndDeleteWhenOwnershipViolationViaHttp() throws Exception {
        String ownerToken = issueAccessToken("http-address-owner@example.com");
        long addressId = createAddressAndReturnId(ownerToken, "owner-only-address");
        String attackerToken = issueAccessToken("http-address-attacker@example.com");

        mockMvc.perform(put("/api/v1/addresses/{addressId}", addressId)
                .header("Authorization", "Bearer " + attackerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "recipientName": "hijack",
                      "recipientPhone": "13800000000",
                      "province": "zhejiang",
                      "city": "hangzhou",
                      "district": "xihu",
                      "detailAddress": "hijack road",
                      "postalCode": "310000",
                      "defaultAddress": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OWNERSHIP_VIOLATION"));

        mockMvc.perform(delete("/api/v1/addresses/{addressId}", addressId)
                .header("Authorization", "Bearer " + attackerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OWNERSHIP_VIOLATION"));
    }

    @Test
    void shouldSupportProtectedAddressWriteViaHttp() throws Exception {
        String accessToken = issueAccessToken("http-protected-write@example.com");
        long addressId = createAddressAndReturnId(accessToken, "protected-write-self");

        mockMvc.perform(post("/api/v1/protected/address/write")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "addressId": %d
                    }
                    """.formatted(addressId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void shouldRejectProtectedAddressWriteWhenOwnershipViolationOrValidationFailedViaHttp() throws Exception {
        String ownerToken = issueAccessToken("http-protected-owner@example.com");
        long addressId = createAddressAndReturnId(ownerToken, "protected-owner-only");
        String attackerToken = issueAccessToken("http-protected-attacker@example.com");

        mockMvc.perform(post("/api/v1/protected/address/write")
                .header("Authorization", "Bearer " + attackerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "addressId": %d
                    }
                    """.formatted(addressId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OWNERSHIP_VIOLATION"));

        mockMvc.perform(post("/api/v1/protected/address/write")
                .header("Authorization", "Bearer " + attackerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
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

    private long createAddressAndReturnId(String accessToken, String detailAddress) throws Exception {
        mockMvc.perform(post("/api/v1/addresses")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "recipientName": "name",
                      "recipientPhone": "13800000000",
                      "province": "zhejiang",
                      "city": "hangzhou",
                      "district": "xihu",
                      "detailAddress": "%s",
                      "postalCode": "310000",
                      "defaultAddress": false
                    }
                    """.formatted(detailAddress)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"));
        return jdbcTemplate.queryForObject(
            "select address_id from user_address where detail_address = ? order by address_id desc limit 1",
            Long.class,
            detailAddress
        );
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
