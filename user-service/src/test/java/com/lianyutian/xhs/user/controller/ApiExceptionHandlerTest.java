package com.lianyutian.xhs.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.lianyutian.xhs.user.controller.handler.ApiExceptionHandler;
import com.lianyutian.xhs.user.controller.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiExceptionHandlerTest {

    @Test
    void shouldNotExposeRawInternalMessage() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/verification/email-code");
        request.setRemoteAddr("127.0.0.1");

        ApiResponse<Void> response = handler.handleIllegalArgument(request, new IllegalArgumentException("No enum constant demo"));

        assertThat(response.code()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void shouldKeepKnownBusinessCodes() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("127.0.0.1");

        ApiResponse<Void> response = handler.handleIllegalArgument(request, new IllegalArgumentException("INVALID_REGISTER_CODE"));

        assertThat(response.code()).isEqualTo("INVALID_REGISTER_CODE");
    }

    @Test
    void shouldExposeRateLimitedCodeForEmailSend() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/verification/email-code");
        request.setRemoteAddr("127.0.0.1");

        ApiResponse<Void> response = handler.handleIllegalArgument(request, new IllegalArgumentException("EMAIL_CODE_RATE_LIMITED"));

        assertThat(response.code()).isEqualTo("EMAIL_CODE_RATE_LIMITED");
    }

    @Test
    void shouldExposeRateLimitedCodeForImageCaptcha() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/verification/image-captcha");
        request.setRemoteAddr("127.0.0.1");

        ApiResponse<Void> response = handler.handleIllegalArgument(request, new IllegalArgumentException("IMAGE_CAPTCHA_RATE_LIMITED"));

        assertThat(response.code()).isEqualTo("IMAGE_CAPTCHA_RATE_LIMITED");
    }

    @Test
    void shouldExposeSendFailedCodeForEmailSend() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/verification/email-code");
        request.setRemoteAddr("127.0.0.1");

        ApiResponse<Void> response = handler.handleIllegalArgument(request, new IllegalArgumentException("EMAIL_CODE_SEND_FAILED"));

        assertThat(response.code()).isEqualTo("EMAIL_CODE_SEND_FAILED");
    }

    @Test
    void shouldHandleUnexpectedExceptionWithUnifiedErrorCode() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.setRemoteAddr("127.0.0.1");

        ApiResponse<Void> response = handler.handleUnexpected(request, new RuntimeException("boom"));

        assertThat(response.code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.message()).isEqualTo("internal server error");
    }

    @Test
    void shouldMaskEmailAlreadyExistsAsRegisterCodeError() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.setRemoteAddr("127.0.0.1");

        ApiResponse<Void> response = handler.handleIllegalArgument(request, new IllegalArgumentException("EMAIL_ALREADY_EXISTS"));

        assertThat(response.code()).isEqualTo("INVALID_REGISTER_CODE");
    }
}
