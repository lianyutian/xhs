package com.lianyutian.xhs.user.repository.mybatis;

import com.lianyutian.xhs.user.model.domain.RefreshTokenStatus;
import com.lianyutian.xhs.user.model.entity.UserRefreshTokenEntity;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserRefreshTokenMapper {

    int insert(UserRefreshTokenEntity entity);

    UserRefreshTokenEntity findByTokenHash(@Param("tokenHash") String tokenHash);

    int updateStatus(@Param("id") Long id, @Param("status") RefreshTokenStatus status, @Param("updatedAt") OffsetDateTime updatedAt);

    int transitionToReplacedIfActive(@Param("id") Long id, @Param("updatedAt") OffsetDateTime updatedAt);

    int revokeActiveBySessionId(@Param("sessionId") Long sessionId, @Param("updatedAt") OffsetDateTime updatedAt);
}
