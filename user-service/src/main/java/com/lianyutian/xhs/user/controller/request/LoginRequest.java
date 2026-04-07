package com.lianyutian.xhs.user.controller.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @Email @NotBlank String email,
    @NotBlank String password,
    String userAgent,
    String captchaToken,
    String captchaAnswer
) {
}
