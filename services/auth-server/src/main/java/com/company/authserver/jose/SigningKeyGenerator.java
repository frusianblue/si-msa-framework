package com.company.authserver.jose;

/**
 * 새 ACTIVE 서명키 1건을 <b>저장형으로</b> 생성한다(키 생성 + 개인키 보호 적용까지). 회전 오케스트레이션({@link SigningKeyRotationService})을
 * Nimbus/암호화 구현으로부터 분리해, 회전 로직을 순수 JDK 단위테스트(스텁 생성기)로 검증할 수 있게 하는 경계.
 */
@FunctionalInterface
public interface SigningKeyGenerator {

    /** 새 RSA 키를 생성하고 개인키를 보호한 뒤 ACTIVE 상태의 {@link SigningKey} 로 반환(retiredAt=null). */
    SigningKey generateActive();
}
