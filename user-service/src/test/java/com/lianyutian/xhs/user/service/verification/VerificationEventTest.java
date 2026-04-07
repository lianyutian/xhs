package com.lianyutian.xhs.user.service.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.code.kaptcha.Producer;
import com.lianyutian.xhs.user.model.domain.VerificationCodePurpose;
import com.lianyutian.xhs.user.service.event.SecurityEventRecorder;
import com.lianyutian.xhs.user.repository.mybatis.VerificationCodeMapper;
import com.lianyutian.xhs.user.service.risk.RedisKeyHelper;
import com.lianyutian.xhs.user.support.AbstractDbIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class VerificationEventTest extends AbstractDbIntegrationTest {

    @Autowired
    private SecurityEventRecorder securityEventRecorder;

    @Autowired
    private VerificationCodeMapper verificationCodeMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisKeyHelper redisKeyHelper;

    @Test
    void shouldRecordEmailSendFailure() {
        DefaultVerificationCodeService service = new DefaultVerificationCodeService(
            (to, subject, content) -> {
                throw new IllegalStateException("mail down");
            },
            5,
            securityEventRecorder,
            Duration.ofMinutes(2),
            Duration.ofMinutes(10),
            redisTemplate,
            redisKeyHelper,
            verificationCodeMapper,
            mock(Producer.class)
        );

        service.issueEmailCode(VerificationCodePurpose.REGISTER, "broken@example.com");

        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from security_event where event_type='VERIFICATION_SEND_FAILED'",
            Integer.class
        );
        assertThat(count).isEqualTo(1);
    }
}
