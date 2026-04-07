package com.lianyutian.xhs.user.model.entity;

import com.lianyutian.xhs.user.model.domain.RefreshTokenStatus;
import java.time.OffsetDateTime;

public class UserRefreshTokenEntity {

    private Long id;
    private Long sessionId;
    private String tokenHash;
    private RefreshTokenStatus status;
    private OffsetDateTime expiresAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public UserRefreshTokenEntity() {
    }

    public UserRefreshTokenEntity(Long id, Long sessionId, String tokenHash, RefreshTokenStatus status, OffsetDateTime expiresAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.tokenHash = tokenHash;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public RefreshTokenStatus getStatus() { return status; }
    public void setStatus(RefreshTokenStatus status) { this.status = status; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
