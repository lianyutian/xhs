package com.lianyutian.xhs.user.service.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.code.kaptcha.Producer;
import com.lianyutian.xhs.user.model.domain.VerificationCodePurpose;
import com.lianyutian.xhs.user.model.domain.VerificationCodeStatus;
import com.lianyutian.xhs.user.service.event.SecurityEventRecorder;
import com.lianyutian.xhs.user.service.integration.mail.MailSenderAdapter;
import com.lianyutian.xhs.user.model.entity.VerificationCodeEntity;
import com.lianyutian.xhs.user.repository.mybatis.VerificationCodeMapper;
import com.lianyutian.xhs.user.service.risk.RedisKeyHelper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

class DefaultVerificationCodeServiceUnitTest {

    @Test
    void shouldPersistPendingThenMarkSentWhenEmailDelivered() {
        MailSenderAdapter mailSenderAdapter = mock(MailSenderAdapter.class);
        SecurityEventRecorder eventRecorder = mock(SecurityEventRecorder.class);
        VerificationCodeMapper verificationCodeMapper = mock(VerificationCodeMapper.class);
        doAnswer(invocation -> {
            VerificationCodeEntity entity = invocation.getArgument(0);
            entity.setId(101L);
            return 1;
        }).when(verificationCodeMapper).insert(any(VerificationCodeEntity.class));
        when(verificationCodeMapper.updateSendResultIfPending(any(), eq(VerificationCodeStatus.SENT), any())).thenReturn(1);

        DefaultVerificationCodeService service = new DefaultVerificationCodeService(
            mailSenderAdapter,
            5,
            eventRecorder,
            Duration.ofMinutes(2),
            Duration.ofMinutes(10),
            mock(StringRedisTemplate.class),
            new RedisKeyHelper("test:user"),
            verificationCodeMapper,
            mock(Producer.class)
        );

        service.issueEmailCode(VerificationCodePurpose.REGISTER, "ok@example.com");

        ArgumentCaptor<VerificationCodeEntity> entityCaptor = ArgumentCaptor.forClass(VerificationCodeEntity.class);
        verify(verificationCodeMapper).insert(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(VerificationCodeStatus.PENDING_SEND);
        verify(verificationCodeMapper).updateSendResultIfPending(any(), eq(VerificationCodeStatus.SENT), any());
        verify(eventRecorder, never()).record(any());
    }

    @Test
    void shouldPersistPendingThenMarkSendFailedWhenMailProviderThrows() {
        MailSenderAdapter mailSenderAdapter = mock(MailSenderAdapter.class);
        doThrow(new IllegalStateException("mail down"))
            .when(mailSenderAdapter)
            .send(any(), any(), any());
        SecurityEventRecorder eventRecorder = mock(SecurityEventRecorder.class);
        VerificationCodeMapper verificationCodeMapper = mock(VerificationCodeMapper.class);
        doAnswer(invocation -> {
            VerificationCodeEntity entity = invocation.getArgument(0);
            entity.setId(102L);
            return 1;
        }).when(verificationCodeMapper).insert(any(VerificationCodeEntity.class));

        DefaultVerificationCodeService service = new DefaultVerificationCodeService(
            mailSenderAdapter,
            5,
            eventRecorder,
            Duration.ofMinutes(2),
            Duration.ofMinutes(10),
            mock(StringRedisTemplate.class),
            new RedisKeyHelper("test:user"),
            verificationCodeMapper,
            mock(Producer.class)
        );

        service.issueEmailCode(VerificationCodePurpose.REGISTER, "broken@example.com");

        ArgumentCaptor<VerificationCodeEntity> entityCaptor = ArgumentCaptor.forClass(VerificationCodeEntity.class);
        verify(verificationCodeMapper).insert(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(VerificationCodeStatus.PENDING_SEND);
        verify(verificationCodeMapper).markSendFailedIfPendingOrReplaced(any(), any());
        verify(eventRecorder).record(any());
    }

    @Test
    void shouldUseCurrentInsertIdForIssueReceipt() {
        MailSenderAdapter mailSenderAdapter = mock(MailSenderAdapter.class);
        SecurityEventRecorder eventRecorder = mock(SecurityEventRecorder.class);
        VerificationCodeMapper verificationCodeMapper = mock(VerificationCodeMapper.class);
        doAnswer(invocation -> {
            VerificationCodeEntity entity = invocation.getArgument(0);
            entity.setId(303L);
            return 1;
        }).when(verificationCodeMapper).insert(any(VerificationCodeEntity.class));
        when(verificationCodeMapper.updateSendResultIfPending(any(), eq(VerificationCodeStatus.SENT), any())).thenReturn(1);

        DefaultVerificationCodeService service = new DefaultVerificationCodeService(
            mailSenderAdapter,
            5,
            eventRecorder,
            Duration.ofMinutes(2),
            Duration.ofMinutes(10),
            mock(StringRedisTemplate.class),
            new RedisKeyHelper("test:user"),
            verificationCodeMapper,
            mock(Producer.class)
        );

        DefaultVerificationCodeService.EmailCodeIssueReceipt receipt = service.issueEmailCodeWithReceipt(
            VerificationCodePurpose.REGISTER,
            "receipt@example.com"
        );

        assertThat(receipt.requestId()).isEqualTo("303");
        verify(verificationCodeMapper, never()).findLatestByPurposeTarget(any(), any());
    }

    @Test
    void shouldRecordFailedAttemptWithEvaluationContextWithoutReevaluating() {
        MailSenderAdapter mailSenderAdapter = mock(MailSenderAdapter.class);
        SecurityEventRecorder eventRecorder = mock(SecurityEventRecorder.class);
        VerificationCodeMapper verificationCodeMapper = mock(VerificationCodeMapper.class);
        VerificationCodeEntity latest = new VerificationCodeEntity(
            501L,
            VerificationCodePurpose.REGISTER,
            "ctx@example.com",
            hash("654321"),
            VerificationCodeStatus.SENT,
            0,
            5,
            OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5),
            OffsetDateTime.now(ZoneOffset.UTC),
            OffsetDateTime.now(ZoneOffset.UTC)
        );
        org.mockito.Mockito.when(verificationCodeMapper.findLatestByPurposeTarget(
            VerificationCodePurpose.REGISTER.name(),
            "ctx@example.com"
        )).thenReturn(latest);

        DefaultVerificationCodeService service = new DefaultVerificationCodeService(
            mailSenderAdapter,
            5,
            eventRecorder,
            Duration.ofMinutes(2),
            Duration.ofMinutes(10),
            mock(StringRedisTemplate.class),
            new RedisKeyHelper("test:user"),
            verificationCodeMapper,
            mock(Producer.class)
        );

        DefaultVerificationCodeService.EmailCodeConsumeAttempt attempt = service.attemptConsumeEmailCodeInCurrentTransaction(
            VerificationCodePurpose.REGISTER,
            "ctx@example.com",
            "wrong"
        );
        assertThat(attempt.verified()).isFalse();
        service.recordFailedEmailCodeAttempt(attempt.failureContext());

        verify(verificationCodeMapper, times(1)).findLatestByPurposeTarget(VerificationCodePurpose.REGISTER.name(), "ctx@example.com");
        verify(verificationCodeMapper).incrementAttemptsAndMaybeFreeze(eq(501L), any());
    }

    @Test
    void shouldReturnSendFailedReceiptWhenSentStatusUpdateFails() {
        MailSenderAdapter mailSenderAdapter = mock(MailSenderAdapter.class);
        SecurityEventRecorder eventRecorder = mock(SecurityEventRecorder.class);
        VerificationCodeMapper verificationCodeMapper = mock(VerificationCodeMapper.class);
        doAnswer(invocation -> {
            VerificationCodeEntity entity = invocation.getArgument(0);
            entity.setId(404L);
            return 1;
        }).when(verificationCodeMapper).insert(any(VerificationCodeEntity.class));
        doThrow(new IllegalStateException("db down"))
            .when(verificationCodeMapper)
            .updateSendResultIfPending(eq(404L), eq(VerificationCodeStatus.SENT), any());

        DefaultVerificationCodeService service = new DefaultVerificationCodeService(
            mailSenderAdapter,
            5,
            eventRecorder,
            Duration.ofMinutes(2),
            Duration.ofMinutes(10),
            mock(StringRedisTemplate.class),
            new RedisKeyHelper("test:user"),
            verificationCodeMapper,
            mock(Producer.class)
        );

        DefaultVerificationCodeService.EmailCodeIssueReceipt receipt = service.issueEmailCodeWithReceipt(
            VerificationCodePurpose.REGISTER,
            "db-fail@example.com"
        );

        assertThat(receipt.sent()).isFalse();
        verify(verificationCodeMapper).markSendFailedIfPendingOrReplaced(eq(404L), any());
        verify(eventRecorder).record(any());
    }

    private String hash(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(messageDigest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
