package com.lianyutian.xhs.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lianyutian.xhs.user.controller.request.LoginRequest;
import com.lianyutian.xhs.user.controller.request.RegisterRequest;
import com.lianyutian.xhs.user.controller.request.SendEmailCodeRequest;
import com.lianyutian.xhs.user.controller.response.SendEmailCodeResponse;
import com.lianyutian.xhs.user.model.domain.VerificationCodePurpose;
import com.lianyutian.xhs.user.support.AbstractDbIntegrationTest;
import io.swagger.v3.oas.annotations.Operation;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthControllerContractTest extends AbstractDbIntegrationTest {

    @Autowired
    private VerificationController verificationController;

    @Autowired
    private AuthController authController;

    @Autowired
    private com.lianyutian.xhs.user.service.verification.DefaultVerificationCodeService verificationCodeService;

    @Autowired
    private ProtectedWriteController protectedWriteController;

    @Test
    void shouldSupportRegisterAndLoginViaController() {
        MockHttpServletRequest sendCodeRequest = new MockHttpServletRequest();
        sendCodeRequest.setRemoteAddr("8.8.4.4");
        SendEmailCodeResponse response = verificationController.sendEmailCode(
            registerEmailCodeRequest("c@example.com"),
            sendCodeRequest
        ).data();
        assertThat(response.code()).isNull();
        assertThat(response.requestId()).isNotBlank();

        String code = verificationCodeService.issueEmailCode(VerificationCodePurpose.REGISTER, "c@example.com");

        var register = authController.register(new RegisterRequest("c@example.com", "Password123!", code));
        assertThat(register.code()).isEqualTo("OK");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("8.8.8.8");
        var login = authController.login(
            new LoginRequest("c@example.com", "Password123!", "agent", null, null),
            request
        );
        assertThat(login.code()).isEqualTo("OK");
        assertThat(login.data().accessToken()).isNotBlank();
    }

    @Test
    void shouldRejectEmailCodeWhenRateLimited() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("9.9.9.9");
        for (int i = 0; i < 5; i++) {
            assertThat(verificationController.sendEmailCode(
                registerEmailCodeRequest("limit@example.com"),
                request
            ).code()).isEqualTo("OK");
        }

        assertThatThrownBy(() -> verificationController.sendEmailCode(
            registerEmailCodeRequest("limit@example.com"),
            request
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("EMAIL_CODE_RATE_LIMITED");

        Integer rateLimitEvents = jdbcTemplate.queryForObject(
            "select count(*) from security_event where event_type='RATE_LIMIT_HIT' and detail='email_code_rate_limited'",
            Integer.class
        );
        assertThat(rateLimitEvents).isEqualTo(1);
    }

    @Test
    void shouldRejectImageCaptchaWhenRateLimited() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("4.4.4.4");
        for (int i = 0; i < 10; i++) {
            assertThat(verificationController.imageCaptcha("LOGIN", request).code()).isEqualTo("OK");
        }

        assertThatThrownBy(() -> verificationController.imageCaptcha("LOGIN", request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("IMAGE_CAPTCHA_RATE_LIMITED");

        Integer rateLimitEvents = jdbcTemplate.queryForObject(
            "select count(*) from security_event where event_type='RATE_LIMIT_HIT' and detail='image_captcha_rate_limited'",
            Integer.class
        );
        assertThat(rateLimitEvents).isEqualTo(1);
    }

    @Test
    void shouldNotConsumeRateLimitQuotaWhenPurposeInvalid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("7.7.7.7");

        assertThatThrownBy(() -> verificationController.sendEmailCode(
            requestWithPurpose("INVALID_PURPOSE", "purpose-invalid@example.com"),
            request
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("INVALID_REQUEST");

        for (int i = 0; i < 5; i++) {
            assertThat(verificationController.sendEmailCode(
                registerEmailCodeRequest("purpose-invalid@example.com"),
                request
            ).code()).isEqualTo("OK");
        }

        assertThatThrownBy(() -> verificationController.sendEmailCode(
            registerEmailCodeRequest("purpose-invalid@example.com"),
            request
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("EMAIL_CODE_RATE_LIMITED");
    }

    @Test
    void shouldRejectRegisterEmailCodeWhenCaptchaScenarioIsLogin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("6.6.6.6");
        var loginChallenge = verificationCodeService.createImageCaptcha("LOGIN");

        assertThatThrownBy(() -> verificationController.sendEmailCode(
            new SendEmailCodeRequest(
                VerificationCodePurpose.REGISTER.name(),
                "captcha-scenario@example.com",
                loginChallenge.token(),
                loginChallenge.answer()
            ),
            request
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("CAPTCHA_REQUIRED");
    }

    @Test
    void shouldUseOpenApiOperationAnnotations() {
        assertThat(hasOperationAnnotation(AuthController.class, "register")).isTrue();
        assertThat(hasOperationAnnotation(AuthController.class, "login")).isTrue();
        assertThat(hasOperationAnnotation(AuthController.class, "refresh")).isTrue();
        assertThat(hasOperationAnnotation(VerificationController.class, "sendEmailCode")).isTrue();
        assertThat(hasOperationAnnotation(ProtectedWriteController.class, "writeAddress")).isTrue();
        assertThat(hasOperationAnnotation(ProtectedWriteController.class, "upload")).isTrue();
    }

    private SendEmailCodeRequest registerEmailCodeRequest(String targetEmail) {
        return requestWithPurpose(VerificationCodePurpose.REGISTER.name(), targetEmail);
    }

    private SendEmailCodeRequest requestWithPurpose(String purpose, String targetEmail) {
        var challenge = verificationCodeService.createImageCaptcha("REGISTER");
        return new SendEmailCodeRequest(
            purpose,
            targetEmail,
            challenge.token(),
            challenge.answer()
        );
    }

    private boolean hasOperationAnnotation(Class<?> type, String methodName) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && method.isAnnotationPresent(Operation.class)) {
                return true;
            }
        }
        return false;
    }
}
