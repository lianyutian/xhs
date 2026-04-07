package com.lianyutian.xhs.user.controller.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
    @Email @NotBlank String email,
    @NotBlank String password,
    @NotBlank String verificationCode
) {
}
