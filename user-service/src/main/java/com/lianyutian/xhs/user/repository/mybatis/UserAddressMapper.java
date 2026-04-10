package com.lianyutian.xhs.user.repository.mybatis;

import com.lianyutian.xhs.user.model.entity.UserAddressEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserAddressMapper {

    int countEffectiveByOwnerUserId(@Param("ownerUserId") Long ownerUserId);

    List<UserAddressEntity> findEffectiveByOwnerUserId(@Param("ownerUserId") Long ownerUserId);

    UserAddressEntity findEffectiveByAddressId(@Param("addressId") Long addressId);

    Long findOwnerUserIdByAddressId(@Param("addressId") Long addressId);

    int insert(UserAddressEntity entity);

    int updateByAddressIdAndOwner(UserAddressEntity entity);

    int softDeleteByAddressIdAndOwner(
        @Param("addressId") Long addressId,
        @Param("ownerUserId") Long ownerUserId,
        @Param("deletedAt") OffsetDateTime deletedAt,
        @Param("updatedAt") OffsetDateTime updatedAt
    );

    int clearDefaultByOwnerUserId(
        @Param("ownerUserId") Long ownerUserId,
        @Param("updatedAt") OffsetDateTime updatedAt
    );
}
