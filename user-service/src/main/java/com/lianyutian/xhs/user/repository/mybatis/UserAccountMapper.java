package com.lianyutian.xhs.user.repository.mybatis;

import com.lianyutian.xhs.user.model.entity.UserAccountEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserAccountMapper {

    int insert(UserAccountEntity entity);

    UserAccountEntity findByEmail(@Param("email") String email);

    UserAccountEntity findById(@Param("id") Long id);
}
