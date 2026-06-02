package com.company.framework.saga;

/**
 * saga 단계 정의: 정방향 커맨드와(선택) 보상 커맨드의 토픽/타입.
 * 보상이 없는 단계(읽기성/되돌릴 필요 없음)는 compensation* 를 null 로 둔다.
 */
public record SagaStep(
        String name, String commandTopic, String commandType, String compensationTopic, String compensationType) {

    public boolean hasCompensation() {
        return compensationType != null && !compensationType.isBlank();
    }
}
