package com.lianyutian.xhs.user.controller;

import com.lianyutian.xhs.user.controller.request.LoginRequest;
import com.lianyutian.xhs.user.controller.request.RefreshRequest;
import com.lianyutian.xhs.user.controller.request.RegisterRequest;
import com.lianyutian.xhs.user.controller.response.AuthTokenResponse;
import com.lianyutian.xhs.user.service.auth.AuthTokens;
import com.lianyutian.xhs.user.service.auth.CurrentUserView;
import com.lianyutian.xhs.user.service.auth.DefaultAuthService;
import com.lianyutian.xhs.user.controller.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "auth")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final DefaultAuthService authService;
    private final SourceIpResolver sourceIpResolver;

    public AuthController(DefaultAuthService authService, SourceIpResolver sourceIpResolver) {
        this.authService = authService;
        this.sourceIpResolver = sourceIpResolver;
    }

    /**
     * 用户注册接口，通过邮箱和验证码完成注册
     *
     * @param request 包含邮箱、密码和验证码的注册请求对象
     * @return 空响应对象
     */
    @Operation(summary = "register by email and verification code")
    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request.email(), request.password(), request.verificationCode());
        return ApiResponse.ok(null);
    }

    /**
     * 用户登录接口，通过邮箱和密码进行身份验证
     *
     * @param request 包含邮箱、密码、验证码等登录信息的请求对象
     * @param servletRequest HTTP 请求对象，用于获取客户端 IP 地址
     * @return 包含访问令牌和刷新令牌的响应对象，登录失败时返回错误码
     */
    @Operation(summary = "login by email and password")
    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        // 调用认证服务执行登录流程
        AuthTokens tokens = authService.login(
            request.email(),
            request.password(),
            sourceIpResolver.resolve(servletRequest),
            request.userAgent() == null ? "unknown" : request.userAgent(),
            request.captchaToken(),
            request.captchaAnswer()
        );

        // 根据认证结果返回相应的响应
        if (tokens.errorCode() != null) {
            return new ApiResponse<>(tokens.errorCode(), "auth failed", null);
        }
        return ApiResponse.ok(new AuthTokenResponse(tokens.accessToken(), tokens.refreshToken()));
    }

    @Operation(summary = "refresh token with rotation")
    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
        AuthTokens tokens = authService.refresh(request.refreshToken(), sourceIpResolver.resolve(servletRequest));
        if (tokens.errorCode() != null) {
            return new ApiResponse<>(tokens.errorCode(), "refresh failed", null);
        }
        return ApiResponse.ok(new AuthTokenResponse(tokens.accessToken(), tokens.refreshToken()));
    }

    @Operation(summary = "logout current session")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        authService.logout(extractBearerToken(authorization));
        return ApiResponse.ok(null);
    }

    @Operation(summary = "get current user from access token")
    @GetMapping("/me")
    public ApiResponse<CurrentUserView> me(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ApiResponse.ok(authService.currentUser(extractBearerToken(authorization)));
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null) {
            return "";
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }
}
