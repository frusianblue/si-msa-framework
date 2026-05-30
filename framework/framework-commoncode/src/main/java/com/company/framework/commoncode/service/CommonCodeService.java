package com.company.framework.commoncode.service;

import com.company.framework.commoncode.domain.CommonCode;
import com.company.framework.commoncode.dto.CommonCodeDto;
import com.company.framework.commoncode.dto.CommonCodeForm;
import com.company.framework.commoncode.mapper.CommonCodeMapper;
import com.company.framework.commoncode.struct.CommonCodeStructMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 공통코드 조회/관리. 그룹 단위로 캐시(Caffeine)하여 반복 조회 비용을 없앤다.
 * 변경 시 해당 그룹 캐시를 무효화한다.
 */
public class CommonCodeService {

    public static final String CACHE = "commonCodes";

    private final CommonCodeMapper mapper;
    private final CommonCodeStructMapper struct;

    public CommonCodeService(CommonCodeMapper mapper, CommonCodeStructMapper struct) {
        this.mapper = mapper;
        this.struct = struct;
    }

    @Cacheable(value = CACHE, key = "#groupCode")
    public List<CommonCodeDto> getByGroup(String groupCode) {
        return struct.toDtoList(mapper.findByGroup(groupCode));
    }

    public List<String> getAllGroups() {
        return mapper.findAllGroups();
    }

    @Transactional
    @CacheEvict(value = CACHE, key = "#form.groupCode()")
    public void create(CommonCodeForm form) {
        CommonCode c = new CommonCode();
        c.setGroupCode(form.groupCode());
        c.setCode(form.code());
        apply(c, form);
        mapper.insert(c);
    }

    @Transactional
    @CacheEvict(value = CACHE, key = "#form.groupCode()")
    public void update(CommonCodeForm form) {
        CommonCode c = new CommonCode();
        c.setGroupCode(form.groupCode());
        c.setCode(form.code());
        apply(c, form);
        mapper.update(c);
    }

    @Transactional
    @CacheEvict(value = CACHE, key = "#groupCode")
    public void delete(String groupCode, String code) {
        mapper.delete(groupCode, code);
    }

    private void apply(CommonCode c, CommonCodeForm form) {
        c.setCodeName(form.codeName());
        c.setCodeValue(form.codeValue());
        c.setSortOrder(form.sortOrder() == null ? 0 : form.sortOrder());
        c.setUseYn(form.useYn() == null || form.useYn());
        c.setAttr1(form.attr1());
        c.setAttr2(form.attr2());
    }
}
