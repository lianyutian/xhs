package com.lianyutian.xhs.user.repository.mybatis;

import com.lianyutian.xhs.user.model.entity.UserProfileEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserProfileMapper {

    int insert(UserProfileEntity entity);

    UserProfileEntity findByUserId(Long userId);
}
