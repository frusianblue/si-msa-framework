package com.company.framework.commoncode.domain;

import com.company.framework.mybatis.handler.BaseEntity;

/**
 * 공통 상세코드. (그룹코드 + 코드 가 PK 역할)
 * 예) groupCode=GENDER, code=M, codeName=남성
 */
public class CommonCode extends BaseEntity {
    private Long id;
    private String groupCode;
    private String code;
    private String codeName;
    private String codeValue;   // 부가 값(선택)
    private int sortOrder;
    private boolean useYn = true;
    private String attr1;       // 확장 속성
    private String attr2;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getGroupCode() { return groupCode; }
    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getCodeName() { return codeName; }
    public void setCodeName(String codeName) { this.codeName = codeName; }
    public String getCodeValue() { return codeValue; }
    public void setCodeValue(String codeValue) { this.codeValue = codeValue; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isUseYn() { return useYn; }
    public void setUseYn(boolean useYn) { this.useYn = useYn; }
    public String getAttr1() { return attr1; }
    public void setAttr1(String attr1) { this.attr1 = attr1; }
    public String getAttr2() { return attr2; }
    public void setAttr2(String attr2) { this.attr2 = attr2; }
}
