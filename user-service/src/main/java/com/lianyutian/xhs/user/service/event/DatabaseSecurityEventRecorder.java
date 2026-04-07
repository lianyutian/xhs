package com.lianyutian.xhs.user.service.event;

import com.lianyutian.xhs.user.model.entity.SecurityEventEntity;
import com.lianyutian.xhs.user.repository.mybatis.SecurityEventMapper;
import java.time.OffsetDateTime;

public class DatabaseSecurityEventRecorder implements SecurityEventRecorder {

    private final SecurityEventMapper securityEventMapper;

    public DatabaseSecurityEventRecorder(SecurityEventMapper securityEventMapper) {
        this.securityEventMapper = securityEventMapper;
    }

    @Override
    public void record(SecurityEvent event) {
        securityEventMapper.insert(new SecurityEventEntity(
            null,
            event.eventType(),
            event.userId(),
            event.sessionId(),
            event.sourceIp(),
            event.detail(),
            OffsetDateTime.ofInstant(event.occurredAt(), java.time.ZoneOffset.UTC)
        ));
    }
}
