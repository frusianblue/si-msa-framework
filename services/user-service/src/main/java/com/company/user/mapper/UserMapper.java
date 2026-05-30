package com.company.user.mapper;

import com.company.framework.core.page.PageRequest;
import com.company.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    int insert(User user);

    Optional<User> findById(@Param("id") Long id);

    Optional<User> findByLoginId(@Param("loginId") String loginId);

    List<User> findPage(@Param("page") PageRequest page);

    long countAll();
}
