package com.lianyutian.xhs.user.repository.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserAddressMapper {

    Long findOwnerUserIdByAddressId(@Param("addressId") Long addressId);
}
