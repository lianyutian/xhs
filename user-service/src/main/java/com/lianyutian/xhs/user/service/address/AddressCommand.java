package com.lianyutian.xhs.user.service.address;

public record AddressCommand(
    String recipientName,
    String recipientPhone,
    String province,
    String city,
    String district,
    String detailAddress,
    String postalCode,
    boolean defaultAddress
) {
}
