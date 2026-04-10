package com.lianyutian.xhs.user.controller;

import com.lianyutian.xhs.user.controller.request.AddressWriteRequest;
import com.lianyutian.xhs.user.controller.request.UploadRequest;
import com.lianyutian.xhs.user.service.auth.CurrentUserView;
import com.lianyutian.xhs.user.service.auth.DefaultAuthService;
import com.lianyutian.xhs.user.controller.response.ApiResponse;
import com.lianyutian.xhs.user.service.address.AddressService;
import com.lianyutian.xhs.user.service.risk.ProtectedWriteGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "protected-write")
@RestController
@RequestMapping("/api/v1/protected")
public class ProtectedWriteController {

    private final DefaultAuthService authService;
    private final ProtectedWriteGuard protectedWriteGuard;
    private final AddressService addressService;

    public ProtectedWriteController(
        DefaultAuthService authService,
        ProtectedWriteGuard protectedWriteGuard,
        AddressService addressService
    ) {
        this.authService = authService;
        this.protectedWriteGuard = protectedWriteGuard;
        this.addressService = addressService;
    }

    /**
     * 地址写操作前的所有权校验接口
     *
     * @param authorization HTTP Authorization 请求头，格式为 "Bearer {token}"
     * @param request 包含待校验地址 ID 的写操作请求对象
     * @return 空响应对象，校验失败时抛出异常
     */
    @Operation(summary = "validate address ownership before write")
    @PostMapping("/address/write")
    public ApiResponse<Void> writeAddress(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
        @Valid @RequestBody AddressWriteRequest request
    ) {
        // 获取当前登录用户信息
        CurrentUserView currentUser = authService.currentUser(extractBearerToken(authorization));

        String resourceId = "address:" + request.addressId();
        try {
            addressService.requireOwnedAddress(currentUser.userId(), request.addressId());
        } catch (IllegalArgumentException ex) {
            if ("OWNERSHIP_VIOLATION".equals(ex.getMessage())) {
                protectedWriteGuard.rejectOwnership(currentUser.userId(), resourceId);
            }
            throw ex;
        }
        return ApiResponse.ok(null);
    }

    @Operation(summary = "validate upload policy and rate limit")
    @PostMapping("/upload")
    public ApiResponse<Void> upload(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
        @Valid @RequestBody UploadRequest request
    ) {
        CurrentUserView currentUser = authService.currentUser(extractBearerToken(authorization));
        protectedWriteGuard.checkUploadPolicy(currentUser.userId(), request.contentType(), request.fileSizeBytes());
        return ApiResponse.ok(null);
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
