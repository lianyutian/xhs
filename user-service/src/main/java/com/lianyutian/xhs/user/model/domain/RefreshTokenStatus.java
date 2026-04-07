package com.lianyutian.xhs.user.model.domain;

public enum RefreshTokenStatus {
    ACTIVE,
    REPLACED,
    REVOKED,
    LEAKED,
    EXPIRED;

    public boolean canTransitionTo(RefreshTokenStatus next) {
        if (this == ACTIVE) {
            return next == REPLACED || next == REVOKED || next == LEAKED || next == EXPIRED;
        }
        return false;
    }
}
