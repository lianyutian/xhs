package com.lianyutian.xhs.user.service.event;

import java.time.Instant;

public record SecurityEvent(String eventType, Long userId, Long sessionId, String sourceIp, String detail, Instant occurredAt) {
}
