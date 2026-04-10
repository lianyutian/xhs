package com.lianyutian.xhs.user.controller.handler;

import com.lianyutian.xhs.user.controller.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final Set<String> EXPOSED_CODES = Set.of(
        "INVALID_REGISTER_CODE",
        "UNAUTHORIZED",
        "INVALID_REFRESH_TOKEN",
        "REFRESH_TOKEN_REPLAYED",
        "SESSION_REVOKED",
        "INVALID_CREDENTIALS",
        "CAPTCHA_REQUIRED",
        "LOGIN_TEMP_BLOCKED",
        "IMAGE_CAPTCHA_RATE_LIMITED",
        "EMAIL_CODE_RATE_LIMITED",
        "EMAIL_CODE_SEND_FAILED",
        "ADDRESS_LIMIT_EXCEEDED",
        "OWNERSHIP_VIOLATION",
        "UPLOAD_RATE_LIMITED",
        "UPLOAD_TYPE_REJECTED",
        "UPLOAD_SIZE_REJECTED"
    );

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(HttpServletRequest request, IllegalArgumentException ex) {
        String code = toClientVisibleCode(ex.getMessage());
        log.warn("request rejected: code={}, path={}, ip={}", code, pathOf(request), ipOf(request));
        return new ApiResponse<>(code, "request rejected", null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(HttpServletRequest request, MethodArgumentNotValidException ex) {
        log.warn(
            "request rejected: code=INVALID_REQUEST, reason=validation_failed, errorCount={}, path={}, ip={}",
            ex.getErrorCount(),
            pathOf(request),
            ipOf(request)
        );
        return new ApiResponse<>("INVALID_REQUEST", "request rejected", null);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpected(HttpServletRequest request, Exception ex) {
        log.error(
            "unexpected server error: path={}, ip={}, type={}",
            pathOf(request),
            ipOf(request),
            ex.getClass().getSimpleName(),
            ex
        );
        return new ApiResponse<>("INTERNAL_SERVER_ERROR", "internal server error", null);
    }

    private String pathOf(HttpServletRequest request) {
        return request == null ? "-" : request.getRequestURI();
    }

    private String ipOf(HttpServletRequest request) {
        return request == null ? "-" : request.getRemoteAddr();
    }

    private String toClientVisibleCode(String internalCode) {
        if ("EMAIL_ALREADY_EXISTS".equals(internalCode)) {
            return "INVALID_REGISTER_CODE";
        }
        if (EXPOSED_CODES.contains(internalCode)) {
            return internalCode;
        }
        return "INVALID_REQUEST";
    }
}
