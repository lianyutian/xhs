package com.lianyutian.xhs.user.model.entity;

import com.lianyutian.xhs.user.model.domain.VerificationCodePurpose;
import com.lianyutian.xhs.user.model.domain.VerificationCodeStatus;
import java.time.OffsetDateTime;

public class VerificationCodeEntity {

    private Long id;
    private VerificationCodePurpose purpose;
    private String target;
    private String codeHash;
    private VerificationCodeStatus status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private OffsetDateTime expiresAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public VerificationCodeEntity() {
    }

    public VerificationCodeEntity(Long id, VerificationCodePurpose purpose, String target, String codeHash, VerificationCodeStatus status, Integer attemptCount, Integer maxAttempts, OffsetDateTime expiresAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.purpose = purpose;
        this.target = target;
        this.codeHash = codeHash;
        this.status = status;
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public VerificationCodePurpose getPurpose() { return purpose; }
    public void setPurpose(VerificationCodePurpose purpose) { this.purpose = purpose; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public VerificationCodeStatus getStatus() { return status; }
    public void setStatus(VerificationCodeStatus status) { this.status = status; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
