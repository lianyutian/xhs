package com.lianyutian.xhs.user.model.domain;

public enum SessionStatus {
    ACTIVE,
    REVOKED,
    EXPIRED;

    public boolean canTransitionTo(SessionStatus next) {
        if (this == ACTIVE) {
            return next == REVOKED || next == EXPIRED;
        }
        return false;
    }
}
