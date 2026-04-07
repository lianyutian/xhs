package com.lianyutian.xhs.user.model.entity;

import java.time.OffsetDateTime;

public class SecurityEventEntity {

    private Long id;
    private String eventType;
    private Long userId;
    private Long sessionId;
    private String sourceIp;
    private String detail;
    private OffsetDateTime createdAt;

    public SecurityEventEntity() {
    }

    public SecurityEventEntity(Long id, String eventType, Long userId, Long sessionId, String sourceIp, String detail, OffsetDateTime createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.userId = userId;
        this.sessionId = sessionId;
        this.sourceIp = sourceIp;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
