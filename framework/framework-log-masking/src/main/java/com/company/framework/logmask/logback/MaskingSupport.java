package com.company.framework.logmask.logback;

import com.company.framework.logmask.mask.SensitiveDataMasker;

/**
 * Logback 컨버터(Spring 컨테이너 밖에서 인스턴스화됨)와 Spring 이 관리하는 {@link SensitiveDataMasker} 빈을 잇는
 * 정적 다리. Logback 의 {@code conversionRule} 로 등록되는 {@link MaskingMessageConverter} 는 DI 를 받을 수 없으므로,
 * 부팅 시 설치기({@code LogMaskingInstaller})가 활성 마스커를 여기에 꽂아 둔다.
 *
 * <p><b>폴백</b>: 마스커가 아직 설치되지 않았거나(=Spring 부팅 전 초기 로그), 이 모듈을 Spring 없이 순수 Logback 으로만
 * 쓰는 경우에도 동작하도록 {@link SensitiveDataMasker#withDefaults()} 로 안전하게 마스킹한다. 즉 "마스킹 미적용으로
 * 원문 PII 노출"은 발생하지 않는다.
 *
 * <p>스레드 안전: {@code volatile} 단일 참조 교체만 한다. 마스커 자체가 불변이라 추가 동기화 불필요.
 */
public final class MaskingSupport {

    private static volatile SensitiveDataMasker masker;
    private static final SensitiveDataMasker FALLBACK = SensitiveDataMasker.withDefaults();

    private MaskingSupport() {}

    /** 설치기가 활성 마스커를 등록(빈 라이프사이클 시작 시). */
    public static void setMasker(SensitiveDataMasker active) {
        masker = active;
    }

    /** 설치기가 마스커를 해제(빈 소멸 시) — 컨테이너 재시작/테스트 격리 대비. */
    public static void clear() {
        masker = null;
    }

    /** 현재 활성 마스커 유무. */
    public static boolean isInstalled() {
        return masker != null;
    }

    /**
     * 메시지를 마스킹. 설치된 마스커가 있으면 그것으로, 없으면 내장 기본 규칙(FALLBACK)으로 처리한다. null 은 null 그대로.
     */
    public static String mask(String message) {
        if (message == null) {
            return null;
        }
        SensitiveDataMasker active = masker;
        return (active != null ? active : FALLBACK).mask(message);
    }
}
