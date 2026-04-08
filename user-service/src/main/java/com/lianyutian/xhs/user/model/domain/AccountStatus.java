package com.lianyutian.xhs.user.model.domain;

/**
 * 用户账户状态枚举
 */
public enum AccountStatus {
    /** 激活状态，允许正常登录 */
    ACTIVE,
    /** 锁定状态（通常因安全原因临时限制） */
    LOCKED,
    /** 禁用状态（永久或长期限制） */
    DISABLED;

    /**
     * 判断当前账户状态是否允许登录
     *
     * @return 如果允许登录返回 true，否则返回 false
     */
    public boolean isLoginAllowed() {
        return this == ACTIVE;
    }
}
