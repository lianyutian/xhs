package com.lianyutian.xhs.user.controller.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 用户登录请求记录类
 *
 * @param email 用户邮箱地址（必填，需符合邮箱格式）
 * @param password 用户密码（必填）
 * @param userAgent 客户端 User-Agent 信息（可选）
 * @param captchaToken 图片验证码令牌（可选，风控触发时必填）
 * @param captchaAnswer 图片验证码答案（可选，风控触发时必填）
 */
public record LoginRequest(
    @Email @NotBlank String email,
    @NotBlank String password,
    String userAgent,
    String captchaToken,
    String captchaAnswer
) {
}
