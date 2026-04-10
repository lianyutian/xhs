package com.lianyutian.xhs.user.controller.response;

import java.time.OffsetDateTime;

public record AddressResponse(
    Long addressId,
    String recipientName,
    String recipientPhone,
    String province,
    String city,
    String district,
    String detailAddress,
    String postalCode,
    boolean defaultAddress,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
