package com.company.framework.logmask.config;

import com.company.framework.logmask.logback.MaskingSupport;
import com.company.framework.logmask.mask.SensitiveDataMasker;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * Spring 이 관리하는 {@link SensitiveDataMasker} 를 Logback 컨버터가 보는 정적 다리({@link MaskingSupport})에
 * 설치/해제하는 라이프사이클 빈. 컨버터는 DI 를 못 받으므로 이 빈이 부팅 시 활성 마스커를 꽂아 준다.
 *
 * <p>{@code install-converter=false} 면 이 빈은 생성되지 않는다(컨버터 미사용 — 빈 직접 호출 경로만 사용).
 * 필드 주입을 금지하는 아키텍처 규칙에 따라 생성자 주입을 쓴다.
 */
public class LogMaskingInstaller implements InitializingBean, DisposableBean {

    private final SensitiveDataMasker masker;

    public LogMaskingInstaller(SensitiveDataMasker masker) {
        this.masker = masker;
    }

    @Override
    public void afterPropertiesSet() {
        MaskingSupport.setMasker(masker);
    }

    @Override
    public void destroy() {
        MaskingSupport.clear();
    }
}
