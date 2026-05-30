package com.company.framework.commoncode.mapper;

import com.company.framework.commoncode.domain.CommonCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommonCodeMapper {
    List<CommonCode> findByGroup(@Param("groupCode") String groupCode);
    List<String> findAllGroups();
    int insert(CommonCode code);
    int update(CommonCode code);
    int delete(@Param("groupCode") String groupCode, @Param("code") String code);
}
