package com.company.authserver.jose;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DB(서명키 테이블) 기반 회전형 JWKSource.
 *
 * <p>다중 파드 K8s 전제 — 서명키를 메모리/파일이 아니라 <b>공유 DB</b>에 두어 모든 파드가 같은 키로 서명/검증한다.
 *
 * <ul>
 *   <li><b>서명</b>: 가장 최신 ACTIVE 키로 서명(SAS 가 {@link #get}에서 첫 매칭을 고름).
 *   <li><b>검증 오버랩</b>: JWKS 셋에는 ACTIVE + RETIRED 를 모두 노출 → 회전 직후에도 이전 키로 서명된 토큰 검증 가능.
 *   <li><b>캐시</b>: DB 조회 결과를 TTL 동안 캐시(회전 전파 지연 = 최대 TTL).
 *   <li><b>부트스트랩</b>: 사용 가능한(=복호화 가능한) ACTIVE 키가 없으면 RSA 2048 1개 생성·삽입. 단순 "행 존재"가
 *       아니라 현재 마스터키로 복호화되는지까지 본다(AES_SECRET 교체로 옛 키만 남은 경우 새 키 자동 생성 — PITFALLS §5).
 *   <li><b>개인키 보호</b>: 저장(부트스트랩)은 {@link SigningKeyCipher#protect}, 읽기는 {@link SigningKeyCipher#reveal}.
 *       읽기는 마커 인지라 평문/암호문 혼재여도 안전(데모→운영 전환·롤백).
 * </ul>
 *
 * <p>회전 자체(주기적 새 ACTIVE 발급 + 직전 키 RETIRE + grace 정리)는 {@link SigningKeyRotationService} 가 담당하고,
 * 다중 파드 중복 회전은 {@code framework-lock} 의 {@code @SchedulerLock}(리더 선출, {@link SigningKeyRotationScheduler})으로 막는다.
 */
public final class JdbcRotatingJwkSource implements JWKSource<SecurityContext> {

    private static final Logger log = LoggerFactory.getLogger(JdbcRotatingJwkSource.class);

    private final SigningKeyMapper mapper;
    private final SigningKeyCipher cipher;
    private final Duration cacheTtl;
    private final Object lock = new Object();
    private volatile Snapshot snapshot;

    private record Snapshot(JWKSet jwkSet, Instant expiresAt) {
        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public JdbcRotatingJwkSource(SigningKeyMapper mapper, SigningKeyCipher cipher, Duration cacheTtl) {
        this.mapper = mapper;
        this.cipher = cipher;
        this.cacheTtl =
                (cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative()) ? Duration.ofMinutes(5) : cacheTtl;
        ensureBootstrapKey();
    }

    /** SAS 가 서명/검증 시 호출. 선택기는 서명이면 kid 미지정 → 최신 ACTIVE 우선이 되도록 셋 순서를 최신순으로 구성한다. */
    @Override
    public List<JWK> get(JWKSelector selector, SecurityContext context) {
        // current() 가 이미 JWKSet 을 반환한다(중복 .jwkSet() 호출 금지).
        return selector.select(current());
    }

    private JWKSet current() {
        Snapshot s = snapshot;
        if (s == null || s.expired()) {
            synchronized (lock) {
                s = snapshot;
                if (s == null || s.expired()) {
                    s = new Snapshot(loadFromDb(), Instant.now().plus(cacheTtl));
                    snapshot = s;
                }
            }
        }
        return s.jwkSet();
    }

    /** ACTIVE + RETIRED 전부 → JWKSet(최신순). 저장형은 cipher.reveal 로 평문 JWK 복원 후 파싱. */
    private JWKSet loadFromDb() {
        List<SigningKey> rows = mapper.findAllUsable();
        List<JWK> jwks = new ArrayList<>(rows.size());
        for (SigningKey row : rows) {
            try {
                jwks.add(RSAKey.parse(cipher.reveal(row.jwkJson())));
            } catch (ParseException | RuntimeException e) {
                // 깨진/복호화 실패 키 1건이 전체 JWKS 를 무너뜨리지 않게 격리(kid 만 로깅, 키 본문 미로깅).
                log.warn("서명키 파싱/복호화 실패 — 건너뜀. kid={}", row.kid());
            }
        }
        if (jwks.isEmpty()) {
            throw new IllegalStateException("사용 가능한 서명키가 없습니다(부트스트랩/복호화 실패 가능).");
        }
        return new JWKSet(jwks);
    }

    /**
     * 사용 가능한(=복호화/파싱 가능한) ACTIVE 키가 없으면 최초 1개 생성.
     *
     * <p>⚠️ 단순히 "ACTIVE 행이 존재하는가"만 보지 않는다. ACTIVE 행이 있어도 그 개인키가 현재 {@link SigningKeyCipher}(=
     * 현재 AES 마스터키)로 복호화되지 않으면 — 예: AES_SECRET 교체 후 옛 키만 남은 경우 — {@link #loadFromDb} 가 그 키를
     * 건너뛰어 JWKS 가 0개가 되고 token/jwks 가 500 으로 떨어진다(부트스트랩이 "행 있음"만 보고 새 키를 안 만들던 함정,
     * PITFALLS §5). 따라서 가장 최신 ACTIVE 키의 복호화 가능 여부까지 확인하고, 불가하면 새 ACTIVE 키를 부트스트랩한다.
     * 복호화 불가 키는 파괴하지 않는다 — {@link #loadFromDb} 가 격리/스킵하므로 무해하고, 보존이 롤백/감사에 안전하다.
     *
     * <p>다중 파드 동시 부트 시 중복 삽입 가능성은 매퍼 unique(kid) + 재조회로 흡수.
     */
    private void ensureBootstrapKey() {
        if (hasUsableActiveKey()) {
            return;
        }
        synchronized (lock) {
            if (hasUsableActiveKey()) {
                return;
            }
            RSAKey generated = generateRsaKey();
            // 부트스트랩도 회전 경로와 동일하게 개인키 보호(평문/암호문 혼재 방지).
            String stored = cipher.protect(generated.toJSONString());
            mapper.insert(SigningKey.active(generated.getKeyID(), stored, Instant.now()));
            log.info("최초 서명키 부트스트랩 완료. kid={}", generated.getKeyID());
        }
    }

    /** 가장 최신 ACTIVE 키가 존재하고 현재 cipher 로 복호화/파싱까지 되는가(= 실제로 서명에 쓸 수 있는가). */
    private boolean hasUsableActiveKey() {
        SigningKey active = mapper.findNewestActive();
        if (active == null) {
            return false;
        }
        try {
            RSAKey.parse(cipher.reveal(active.jwkJson()));
            return true;
        } catch (ParseException | RuntimeException e) {
            // 복호화/파싱 실패 = 현재 마스터키로 못 쓰는 키. 새 ACTIVE 를 부트스트랩하도록 false 반환(키 본문 미로깅).
            log.warn("기존 ACTIVE 서명키를 현재 마스터키로 복호화/파싱할 수 없음 — 새 키를 부트스트랩한다. kid={}", active.kid());
            return false;
        }
    }

    /** RS256 RSA 2048 키 생성(개인키 포함). 순수 JDK + Nimbus. 회전 스케줄러가 같은 패키지에서 재사용(가시성 승격 불필요). */
    static RSAKey generateRsaKey() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair pair = gen.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("RSA 서명키 생성 실패", e);
        }
    }
}
