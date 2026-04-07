package com.lianyutian.xhs.user.service.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lianyutian.xhs.user.support.AbstractDbIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProtectedWriteRiskControlTest extends AbstractDbIntegrationTest {

    @Autowired
    private ProtectedWriteGuard guard;

    @Test
    void shouldRejectWriteWhenOwnershipMissing() {
        assertThatThrownBy(() -> guard.assertOwnership(1L, 2L, "address:8"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("OWNERSHIP_VIOLATION");

        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from security_event where event_type='OWNERSHIP_FAILED'",
            Integer.class
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldApplyUploadTypeAndSizePolicy() {
        assertThatThrownBy(() -> guard.checkUploadPolicy(1L, "application/pdf", 100))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("UPLOAD_TYPE_REJECTED");

        assertThatThrownBy(() -> guard.checkUploadPolicy(1L, "image/png", 6 * 1024 * 1024))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("UPLOAD_SIZE_REJECTED");
    }

    @Test
    void shouldApplyUploadRateLimitToAllAttempts() {
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> guard.checkUploadPolicy(2L, "application/pdf", 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UPLOAD_TYPE_REJECTED");
        }

        assertThatThrownBy(() -> guard.checkUploadPolicy(2L, "image/png", 100))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("UPLOAD_RATE_LIMITED");
    }
}
