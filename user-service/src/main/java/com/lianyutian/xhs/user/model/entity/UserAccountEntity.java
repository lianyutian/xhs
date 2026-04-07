package com.lianyutian.xhs.user.model.entity;

import com.lianyutian.xhs.user.model.domain.AccountStatus;
import java.time.OffsetDateTime;

public class UserAccountEntity {

    private Long id;
    private String email;
    private String passwordHash;
    private AccountStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public UserAccountEntity() {
    }

    public UserAccountEntity(Long id, String email, String passwordHash, AccountStatus status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
