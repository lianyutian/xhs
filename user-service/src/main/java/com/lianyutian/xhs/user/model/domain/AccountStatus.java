package com.lianyutian.xhs.user.model.domain;

public enum AccountStatus {
    ACTIVE,
    LOCKED,
    DISABLED;

    public boolean isLoginAllowed() {
        return this == ACTIVE;
    }
}
