package com.example.batchtypes.jobs.mybatis;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

/**
 * 매퍼 — XML(`classpath:mapper/SettlementMapper.xml`)의 statement 와 1:1.
 * 페이징 리더는 statement id 문자열({@code ...SettlementMapper.selectTxnPage})로 호출하고,
 * MyBatis 가 {@code _page}/{@code _pagesize}/{@code _skiprows} 를 파라미터로 넘긴다.
 */
@Mapper
public interface SettlementMapper {

    List<SettlementMb> selectTxnPage(Map<String, Object> params);

    void insertOut(SettlementMb row);
}
