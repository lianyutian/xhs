package com.lianyutian.xhs.user.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressUpsertRequest(
    @NotBlank @Size(max = 64) String recipientName,
    @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String recipientPhone,
    @NotBlank @Size(max = 64) String province,
    @NotBlank @Size(max = 64) String city,
    @NotBlank @Size(max = 64) String district,
    @NotBlank @Size(max = 255) String detailAddress,
    @Size(max = 32) String postalCode,
    @NotNull Boolean defaultAddress
) {
}
