package com.company.framework.commoncode.struct;

import com.company.framework.commoncode.domain.CommonCode;
import com.company.framework.commoncode.dto.CommonCodeDto;
import java.util.List;
import org.mapstruct.Mapper;

/**
 * 도메인 -> DTO 변환 (MapStruct 컴파일타임 생성).
 * componentModel 기본값 사용 -> Mappers.getMapper 로 인스턴스화(자동설정에서 빈 등록).
 */
@Mapper
public interface CommonCodeStructMapper {
    CommonCodeDto toDto(CommonCode entity);

    List<CommonCodeDto> toDtoList(List<CommonCode> entities);
}
