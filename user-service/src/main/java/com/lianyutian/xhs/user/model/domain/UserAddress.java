package com.lianyutian.xhs.user.model.domain;

import java.time.OffsetDateTime;

/**
 * 用户收货地址领域模型
 *
 * @param addressId 地址唯一标识 ID
 * @param ownerUserId 所属用户 ID
 * @param recipientName 收件人姓名
 * @param recipientPhone 收件人手机号
 * @param province 省份
 * @param city 城市
 * @param district 区县
 * @param detailAddress 详细地址
 * @param postalCode 邮政编码
 * @param defaultAddress 是否为默认地址
 * @param createdAt 创建时间（UTC）
 * @param updatedAt 更新时间（UTC）
 */
public record UserAddress(
    Long addressId,
    Long ownerUserId,
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
