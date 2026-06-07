package com.company.framework.security.rbac.spi;

import com.company.framework.security.rbac.domain.Menu;
import java.util.List;

/**
 * RBAC 메뉴 조회용 영속 포트(SPI).
 *
 * <p>보안 코어의 {@code MenuService}/{@code MenuController} 는 메뉴를 <b>이 포트로만</b> 조회한다.
 * 실제 구현(MyBatis 등)은 어댑터 모듈이 제공한다(예: {@code MyBatisMenuProvider}).
 *
 * <p>이 포트 빈이 존재하고 {@code framework.security.menu=true}(기본) 일 때만 메뉴 API 가 활성화된다.
 */
public interface MenuProvider {

    /** 주어진 역할들이 접근 가능한 메뉴 목록(평면). 트리 변환은 코어 {@code MenuService} 가 수행. */
    List<Menu> findMenusByRoles(List<String> roles);
}
