package com.lianyutian.xhs.user.repository.mybatis;

import com.lianyutian.xhs.user.model.entity.SecurityEventEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SecurityEventMapper {

    int insert(SecurityEventEntity entity);
}
