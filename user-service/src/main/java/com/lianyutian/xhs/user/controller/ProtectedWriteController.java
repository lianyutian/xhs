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

    @Operation(summary = "validate address ownership before write")
    @PostMapping("/address/write")
    public ApiResponse<Void> writeAddress(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
        @Valid @RequestBody AddressWriteRequest request
    ) {
        CurrentUserView currentUser = authService.currentUser(extractBearerToken(authorization));
        Long ownerUserId = userAddressMapper.findOwnerUserIdByAddressId(request.addressId());
        String resourceId = "address:" + request.addressId();
        if (ownerUserId == null) {
            protectedWriteGuard.rejectOwnership(currentUser.userId(), resourceId);
        }
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
