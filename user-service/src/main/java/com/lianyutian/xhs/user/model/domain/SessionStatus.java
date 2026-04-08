package com.lianyutian.xhs.user.model.domain;

/**
 * 用户会话状态枚举
 */
public enum SessionStatus {
    /** 活跃状态 */
    ACTIVE,
    /** 已撤销（用户主动登出或安全策略） */
    REVOKED,
    /** 已过期 */
    EXPIRED;

    /**
     * 判断当前状态是否可以转换到目标状态
     *
     * @param next 目标状态
     * @return 如果可以转换返回 true，否则返回 false
     */
    public boolean canTransitionTo(SessionStatus next) {
        // 只有 ACTIVE 状态可以转换到 REVOKED 或 EXPIRED
        if (this == ACTIVE) {
            return next == REVOKED || next == EXPIRED;
        }
        // 其他状态为终态，不允许再转换
        return false;
    }
}
