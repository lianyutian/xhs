package com.lianyutian.xhs.user.controller;

import com.lianyutian.xhs.user.controller.request.AddressUpsertRequest;
import com.lianyutian.xhs.user.controller.response.AddressResponse;
import com.lianyutian.xhs.user.controller.response.ApiResponse;
import com.lianyutian.xhs.user.model.domain.UserAddress;
import com.lianyutian.xhs.user.service.address.AddressCommand;
import com.lianyutian.xhs.user.service.address.AddressService;
import com.lianyutian.xhs.user.service.auth.CurrentUserView;
import com.lianyutian.xhs.user.service.auth.DefaultAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户地址管理控制器，提供地址的增删改查接口
 */
@Tag(name = "address")
@RestController
@RequestMapping("/api/v1/addresses")
public class AddressController {

    private final DefaultAuthService authService;
    private final AddressService addressService;

    public AddressController(DefaultAuthService authService, AddressService addressService) {
        this.authService = authService;
        this.addressService = addressService;
    }

    /**
     * 获取当前登录用户的地址列表
     *
     * @param authorization HTTP Authorization 请求头，格式为 "Bearer {token}"
     * @return 包含地址信息的响应列表
     */
    @Operation(summary = "list current user addresses")
    @GetMapping
    public ApiResponse<List<AddressResponse>> listAddresses(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        Long userId = currentUser(authorization).userId();
        List<AddressResponse> response = addressService.list(userId).stream().map(this::toResponse).toList();
        return ApiResponse.ok(response);
    }

    /**
     * 创建新的收货地址
     *
     * @param authorization HTTP Authorization 请求头，格式为 "Bearer {token}"
     * @param request 包含收件人信息和详细地址的请求对象
     * @return 新创建的地址信息
     */
    @Operation(summary = "create current user address")
    @PostMapping
    public ApiResponse<AddressResponse> createAddress(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
        @Valid @RequestBody AddressUpsertRequest request
    ) {
        Long userId = currentUser(authorization).userId();
        UserAddress created = addressService.create(userId, toCommand(request));
        return ApiResponse.ok(toResponse(created));
    }

    /**
     * 更新指定的收货地址（全量替换）
     *
     * @param authorization HTTP Authorization 请求头，格式为 "Bearer {token}"
     * @param addressId 待更新的地址 ID
     * @param request 包含收件人信息和详细地址的请求对象
     * @return 更新后的地址信息
     */
    @Operation(summary = "replace current user address")
    @PutMapping("/{addressId}")
    public ApiResponse<AddressResponse> updateAddress(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
        @PathVariable Long addressId,
        @Valid @RequestBody AddressUpsertRequest request
    ) {
        Long userId = currentUser(authorization).userId();
        UserAddress updated = addressService.update(userId, addressId, toCommand(request));
        return ApiResponse.ok(toResponse(updated));
    }

    /**
     * 删除指定的收货地址
     *
     * @param authorization HTTP Authorization 请求头，格式为 "Bearer {token}"
     * @param addressId 待删除的地址 ID
     * @return 空响应对象
     */
    @Operation(summary = "delete current user address")
    @DeleteMapping("/{addressId}")
    public ApiResponse<Void> deleteAddress(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
        @PathVariable Long addressId
    ) {
        Long userId = currentUser(authorization).userId();
        addressService.delete(userId, addressId);
        return ApiResponse.ok(null);
    }

    /**
     * 从 Authorization 请求头中提取当前登录用户信息
     *
     * @param authorization HTTP Authorization 请求头
     * @return 当前用户视图对象
     */
    private CurrentUserView currentUser(String authorization) {
        return authService.currentUser(extractBearerToken(authorization));
    }

    /**
     * 从 Bearer Token 格式的字符串中提取纯访问令牌
     *
     * @param authorization 认证头部值
     * @return 纯访问令牌字符串
     */
    private String extractBearerToken(String authorization) {
        if (authorization == null) {
            return "";
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }

    /**
     * 将前端请求对象转换为内部业务命令对象
     *
     * @param request 地址新增或更新请求对象
     * @return 地址业务命令对象
     */
    private AddressCommand toCommand(AddressUpsertRequest request) {
        return new AddressCommand(
            request.recipientName(),
            request.recipientPhone(),
            request.province(),
            request.city(),
            request.district(),
            request.detailAddress(),
            request.postalCode(),
            Boolean.TRUE.equals(request.defaultAddress())
        );
    }

    /**
     * 将领域模型转换为 API 响应对象
     *
     * @param address 地址领域模型
     * @return 地址响应对象
     */
    private AddressResponse toResponse(UserAddress address) {
        return new AddressResponse(
            address.addressId(),
            address.recipientName(),
            address.recipientPhone(),
            address.province(),
            address.city(),
            address.district(),
            address.detailAddress(),
            address.postalCode(),
            address.defaultAddress(),
            address.createdAt(),
            address.updatedAt()
        );
    }
}
