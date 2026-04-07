package com.lianyutian.xhs.user.repository.mybatis;

import com.lianyutian.xhs.user.model.domain.SessionStatus;
import com.lianyutian.xhs.user.model.entity.UserSessionEntity;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserSessionMapper {

    int insert(UserSessionEntity entity);

    UserSessionEntity findById(@Param("id") Long id);

    UserSessionEntity findLatestByUserId(@Param("userId") Long userId);

    int updateStatus(@Param("id") Long id, @Param("status") SessionStatus status, @Param("updatedAt") OffsetDateTime updatedAt);

    int updateExpiresAt(@Param("id") Long id, @Param("expiresAt") OffsetDateTime expiresAt, @Param("updatedAt") OffsetDateTime updatedAt);
}
