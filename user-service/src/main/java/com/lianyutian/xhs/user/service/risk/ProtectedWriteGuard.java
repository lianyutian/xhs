package com.lianyutian.xhs.user.service.risk;

import com.lianyutian.xhs.user.service.event.SecurityEvent;
import com.lianyutian.xhs.user.service.event.SecurityEventRecorder;
import java.time.Instant;
import java.util.Set;

public class ProtectedWriteGuard {

    private static final Set<String> ALLOWED_UPLOAD_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final SecurityEventRecorder eventRecorder;
    private final RiskControlService riskControlService;
    private final int uploadRateLimit;
    private final long maxUploadBytes;

    public ProtectedWriteGuard(SecurityEventRecorder eventRecorder, RiskControlService riskControlService, int uploadRateLimit, long maxUploadBytes) {
        this.eventRecorder = eventRecorder;
        this.riskControlService = riskControlService;
        this.uploadRateLimit = uploadRateLimit;
        this.maxUploadBytes = maxUploadBytes;
    }

    public void assertOwnership(Long authenticatedUserId, Long resourceOwnerUserId, String resourceId) {
        if (!authenticatedUserId.equals(resourceOwnerUserId)) {
            rejectOwnership(authenticatedUserId, resourceId);
        }
    }

    public void rejectOwnership(Long authenticatedUserId, String resourceId) {
        eventRecorder.record(new SecurityEvent(
            "OWNERSHIP_FAILED",
            authenticatedUserId,
            null,
            null,
            "resource=" + resourceId,
            Instant.now()
        ));
        throw new IllegalArgumentException("OWNERSHIP_VIOLATION");
    }

    public void checkUploadPolicy(Long userId, String contentType, long fileSizeBytes) {
        int count = riskControlService.incrementUploadCount(userId);
        if (count > uploadRateLimit) {
            eventRecorder.record(new SecurityEvent(
                "UPLOAD_RATE_LIMITED",
                userId,
                null,
                null,
                "count=" + count,
                Instant.now()
            ));
            throw new IllegalArgumentException("UPLOAD_RATE_LIMITED");
        }
        if (!ALLOWED_UPLOAD_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("UPLOAD_TYPE_REJECTED");
        }
        if (fileSizeBytes > maxUploadBytes) {
            throw new IllegalArgumentException("UPLOAD_SIZE_REJECTED");
        }
    }
}
