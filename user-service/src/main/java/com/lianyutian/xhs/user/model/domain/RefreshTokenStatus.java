package com.lianyutian.xhs.user.model.domain;

/**
 * 刷新令牌状态枚举
 */
public enum RefreshTokenStatus {
    /** 激活可用 */
    ACTIVE,
    /** 已被替换（令牌轮换） */
    REPLACED,
    /** 已被撤销（用户主动登出或安全策略） */
    REVOKED,
    /** 疑似泄露（异常使用检测） */
    LEAKED,
    /** 已过期 */
    EXPIRED;

    /**
     * 判断当前状态是否可以转换到目标状态
     *
     * @param next 目标状态
     * @return 如果可以转换返回 true，否则返回 false
     */
    public boolean canTransitionTo(RefreshTokenStatus next) {
        // 只有 ACTIVE 状态可以转换到其他状态
        if (this == ACTIVE) {
            return next == REPLACED || next == REVOKED || next == LEAKED || next == EXPIRED;
        }
        // 其他状态为终态，不允许再转换
        return false;
    }
}
