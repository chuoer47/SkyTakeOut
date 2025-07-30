package com.sky.mapper;

import com.sky.annotation.AutoFill;
import com.sky.entity.User;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

@Mapper
public interface UserMapper {

    User getByOpenid(String openid);

    void insert(User user);

    User getById(Long userId);

    Integer countByMap(Map map);
}
