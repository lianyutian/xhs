package com.lianyutian.xhs.user.model.domain;

/**
 * 验证码状态枚举
 */
public enum VerificationCodeStatus {
    /** 待发送 */
    PENDING_SEND,
    /** 已发送 */
    SENT,
    /** 发送失败 */
    SEND_FAILED,
    /** 激活可用 */
    ACTIVE,
    /** 已被替换（新验证码生成） */
    REPLACED,
    /** 已使用（验证成功） */
    CONSUMED,
    /** 已冻结（异常锁定） */
    FROZEN,
    /** 已过期 */
    EXPIRED;

    /**
     * 判断当前状态是否可以转换到目标状态
     *
     * @param next 目标状态
     * @return 如果可以转换返回 true，否则返回 false
     */
    public boolean canTransitionTo(VerificationCodeStatus next) {
        // PENDING_SEND 可以转换到 SENT、SEND_FAILED 或 ACTIVE
        if (this == PENDING_SEND) {
            return next == SENT || next == SEND_FAILED || next == ACTIVE;
        }
        // SENT 和 ACTIVE 可以转换到 REPLACED、CONSUMED、FROZEN 或 EXPIRED
        if (this == SENT || this == ACTIVE) {
            return next == REPLACED || next == CONSUMED || next == FROZEN || next == EXPIRED;
        }
        // 其他状态不允许转换
        return false;
    }
}
