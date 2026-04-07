package com.lianyutian.xhs.user.repository.mybatis;

import com.lianyutian.xhs.user.model.domain.VerificationCodeStatus;
import com.lianyutian.xhs.user.model.entity.VerificationCodeEntity;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VerificationCodeMapper {

    int insert(VerificationCodeEntity entity);

    VerificationCodeEntity findLatestActive(@Param("purpose") String purpose, @Param("target") String target);

    VerificationCodeEntity findLatestVerifiable(@Param("purpose") String purpose, @Param("target") String target);

    VerificationCodeEntity findLatestByPurposeTarget(@Param("purpose") String purpose, @Param("target") String target);

    int markActiveAsReplaced(@Param("purpose") String purpose, @Param("target") String target, @Param("updatedAt") OffsetDateTime updatedAt);

    int markVerifiableAsReplaced(@Param("purpose") String purpose, @Param("target") String target, @Param("updatedAt") OffsetDateTime updatedAt);

    int markOlderVerifiableAsReplaced(
        @Param("purpose") String purpose,
        @Param("target") String target,
        @Param("currentId") Long currentId,
        @Param("updatedAt") OffsetDateTime updatedAt
    );

    int updateStatusAndAttempts(
        @Param("id") Long id,
        @Param("status") VerificationCodeStatus status,
        @Param("attemptCount") Integer attemptCount,
        @Param("updatedAt") OffsetDateTime updatedAt
    );

    int markExpiredIfVerifiable(@Param("id") Long id, @Param("updatedAt") OffsetDateTime updatedAt);

    int markFrozenIfVerifiable(@Param("id") Long id, @Param("updatedAt") OffsetDateTime updatedAt);

    int incrementAttemptsAndMaybeFreeze(@Param("id") Long id, @Param("updatedAt") OffsetDateTime updatedAt);

    int updateSendResultIfPending(
        @Param("id") Long id,
        @Param("status") VerificationCodeStatus status,
        @Param("updatedAt") OffsetDateTime updatedAt
    );

    int markSendFailedIfPendingOrReplaced(@Param("id") Long id, @Param("updatedAt") OffsetDateTime updatedAt);

    int consumeIfVerifiable(@Param("id") Long id, @Param("updatedAt") OffsetDateTime updatedAt);
}
