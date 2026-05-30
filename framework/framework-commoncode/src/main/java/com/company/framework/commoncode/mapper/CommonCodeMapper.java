package com.company.framework.commoncode.mapper;

import com.company.framework.commoncode.domain.CommonCode;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommonCodeMapper {
    List<CommonCode> findByGroup(@Param("groupCode") String groupCode);

    List<String> findAllGroups();

    int insert(CommonCode code);

    int update(CommonCode code);

    int delete(@Param("groupCode") String groupCode, @Param("code") String code);
}
