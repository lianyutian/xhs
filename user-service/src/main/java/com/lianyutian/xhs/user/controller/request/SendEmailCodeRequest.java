package com.lianyutian.xhs.user.controller.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendEmailCodeRequest(
    @NotBlank String purpose,
    @Email @NotBlank String targetEmail,
    String captchaToken,
    String captchaAnswer
) {
}
