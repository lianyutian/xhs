package com.lianyutian.xhs.user.controller.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UploadRequest(@NotBlank String contentType, @Min(1) long fileSizeBytes) {
}
