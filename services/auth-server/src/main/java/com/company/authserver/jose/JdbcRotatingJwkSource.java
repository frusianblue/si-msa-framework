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
 *   <li><b>부트스트랩</b>: 키가 하나도 없으면 RSA 2048 1개 생성·삽입(최초 1회).
 * </ul>
 *
 * <p>⚠️ 회전 자체(주기적 새 ACTIVE 발급 + 오래된 키 RETIRE)는 별도 스케줄 작업의 책임이다. 다중 파드에서 중복 회전을 막으려면
 * {@code framework-lock} 의 {@code @SchedulerLock}(리더 선출)으로 단일 파드만 회전하게 한다. 본 골격은 읽기 측 + 부트스트랩만 구현(회전 스케줄러는 확장점).
 */
public final class JdbcRotatingJwkSource implements JWKSource<SecurityContext> {

    private static final Logger log = LoggerFactory.getLogger(JdbcRotatingJwkSource.class);

    private final SigningKeyMapper mapper;
    private final Duration cacheTtl;
    private final Object lock = new Object();
    private volatile Snapshot snapshot;

    private record Snapshot(JWKSet jwkSet, Instant expiresAt) {
        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public JdbcRotatingJwkSource(SigningKeyMapper mapper, Duration cacheTtl) {
        this.mapper = mapper;
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

    /** ACTIVE + RETIRED 전부 → JWKSet(최신순). 서명 후보(ACTIVE)가 앞에 오도록 정렬은 매퍼 쿼리(created desc)에 위임. */
    private JWKSet loadFromDb() {
        List<SigningKey> rows = mapper.findAllUsable();
        List<JWK> jwks = new ArrayList<>(rows.size());
        for (SigningKey row : rows) {
            try {
                jwks.add(RSAKey.parse(row.jwkJson()));
            } catch (ParseException e) {
                // 깨진 키 1건이 전체 JWKS 를 무너뜨리지 않게 격리(kid 만 로깅, 키 본문 미로깅).
                log.warn("서명키 파싱 실패 — 건너뜀. kid={}", row.kid());
            }
        }
        if (jwks.isEmpty()) {
            throw new IllegalStateException("사용 가능한 서명키가 없습니다(부트스트랩 실패 가능).");
        }
        return new JWKSet(jwks);
    }

    /** 키가 전무하면 최초 1개 생성. 다중 파드 동시 부트 시 중복 삽입 가능성은 매퍼 unique(kid) + 재조회로 흡수. */
    private void ensureBootstrapKey() {
        if (mapper.findNewestActive() != null) {
            return;
        }
        synchronized (lock) {
            if (mapper.findNewestActive() != null) {
                return;
            }
            RSAKey generated = generateRsaKey();
            mapper.insert(
                    new SigningKey(generated.getKeyID(), generated.toJSONString(), SigningKey.ACTIVE, Instant.now()));
            log.info("최초 서명키 부트스트랩 완료. kid={}", generated.getKeyID());
            // TODO(운영): jwkJson 평문 저장 금지 — 컬럼 암호화/KMS/Vault 로 개인키 보호.
            // TODO(운영): 부트스트랩은 데모 편의. 운영은 회전 스케줄러(framework-lock 리더 선출)에서 관리 권장.
        }
    }

    /** RS256 RSA 2048 키 생성(개인키 포함). 순수 JDK + Nimbus. */
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
