package com.lianyutian.xhs.user.controller.request;

import jakarta.validation.constraints.NotNull;

public record AddressWriteRequest(@NotNull Long addressId) {
}
