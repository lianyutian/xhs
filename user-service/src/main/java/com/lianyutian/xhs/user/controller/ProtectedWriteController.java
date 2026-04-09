package com.lianyutian.xhs.user.controller;

import com.lianyutian.xhs.user.controller.request.AddressWriteRequest;
import com.lianyutian.xhs.user.controller.request.UploadRequest;
import com.lianyutian.xhs.user.service.auth.CurrentUserView;
import com.lianyutian.xhs.user.service.auth.DefaultAuthService;
import com.lianyutian.xhs.user.controller.response.ApiResponse;
import com.lianyutian.xhs.user.repository.mybatis.UserAddressMapper;
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
    private final UserAddressMapper userAddressMapper;

    public ProtectedWriteController(
        DefaultAuthService authService,
        ProtectedWriteGuard protectedWriteGuard,
        UserAddressMapper userAddressMapper
    ) {
        this.authService = authService;
        this.protectedWriteGuard = protectedWriteGuard;
        this.userAddressMapper = userAddressMapper;
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

        // 查询目标地址的所有者用户 ID
        Long ownerUserId = userAddressMapper.findOwnerUserIdByAddressId(request.addressId());
        String resourceId = "address:" + request.addressId();

        // 如果地址不存在，执行拒绝逻辑（通常用于防止通过错误提示枚举资源）
        if (ownerUserId == null) {
            protectedWriteGuard.rejectOwnership(currentUser.userId(), resourceId);
        }

        // 校验当前用户是否为地址所有者，非所有者将抛出异常
        protectedWriteGuard.assertOwnership(
            currentUser.userId(),
            ownerUserId,
            resourceId
        );
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
