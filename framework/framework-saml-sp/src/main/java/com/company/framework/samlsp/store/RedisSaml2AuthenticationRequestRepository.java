package com.company.framework.samlsp.store;

import com.company.framework.samlsp.config.SamlSpProperties;
import com.company.framework.samlsp.store.Saml2AuthnRequestCodec.Data;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.saml2.provider.service.authentication.AbstractSaml2AuthenticationRequest;
import org.springframework.security.saml2.provider.service.authentication.Saml2PostAuthenticationRequest;
import org.springframework.security.saml2.provider.service.authentication.Saml2RedirectAuthenticationRequest;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;
import org.springframework.security.saml2.provider.service.web.Saml2AuthenticationRequestRepository;

/**
 * 멀티 파드용 {@link Saml2AuthenticationRequestRepository} redis 구현. 기본 {@code HttpSession} 저장소는 SP-initiated
 * 흐름의 AuthnRequest↔Response 상관을 서버 세션(JSESSIONID)에 묶는데, 게이트웨이가 authorize 와 ACS 콜백을 서로 다른
 * 파드로 보내면 세션이 없어 인증이 깨진다. 본 구현은 <b>세션 대신 상관관계 쿠키 + redis</b>로 파드 간 공유한다
 * (OAuth {@code RedisOAuthStateStore} 와 같은 결 — 공유 저장소 + TTL 네이티브 만료).
 *
 * <p><b>동작:</b>
 *
 * <ul>
 *   <li><b>save</b>(authorize 단계): 불투명 1회용 키(UUID)를 생성 → 직렬화된 AuthnRequest 를 {@code keyPrefix+key}
 *       로 redis 에 TTL 저장 → 키를 상관관계 쿠키로 응답에 심는다.
 *   <li><b>load</b>(ACS 검증): 쿠키에서 키를 읽어 redis GET → 코덱 복원(읽기 전용).
 *   <li><b>remove</b>(ACS 소비): 쿠키 키로 redis GETDEL(원자적 1회 소비, 재생 차단) → 쿠키 삭제 → 복원 반환.
 * </ul>
 *
 * <p><b>⚠️ 쿠키 SameSite 함정(필독, 되돌리지 말 것):</b> SAML <b>POST 바인딩</b> ACS 콜백은 IdP 가 돌려준 자동제출
 * 폼에 의한 <b>크로스사이트 top-level POST</b>다. 브라우저는 {@code SameSite=Lax/Strict} 쿠키를 크로스사이트 POST
 * 네비게이션에 <b>실어 보내지 않는다</b> → 상관관계 쿠키가 콜백에서 사라져 인증이 깨진다. 따라서 이 쿠키는
 * {@code SameSite=None; Secure} 여야 한다(=HTTPS 필수, 운영 k8s 인그레스 TLS 에서 정상). 로컬 평문 HTTP 개발에서는
 * {@code Secure} 쿠키가 왕복하지 않으므로 {@code request-repository=session} 을 쓴다. ({@code SameSite=None} +
 * {@code cookie-secure=false} 조합은 오토컨피그가 시작 시 fail-fast — 브라우저가 조용히 쿠키를 버리는 함정 차단.)
 *
 * <p><b>복원에 RelyingPartyRegistration 이 필요한 이유:</b> SS 의 요청 빌더는 {@code withRelyingPartyRegistration}
 * 정적 팩토리만 public 이라(무인자 빌더는 deprecated·protected) 복원 시 등록 정보가 필요하다. 저장된
 * {@code relyingPartyRegistrationId} 로 {@link RelyingPartyRegistrationRepository} 에서 조회해 재구성한다(등록을 못 찾으면
 * 복원 불가 → {@code null} 반환 → SS 가 "요청 없음"으로 명확히 실패). 직렬화는 {@link Saml2AuthnRequestCodec}(고정형,
 * Jackson/네이티브직렬화 비의존)가 담당한다.
 */
public final class RedisSaml2AuthenticationRequestRepository
        implements Saml2AuthenticationRequestRepository<AbstractSaml2AuthenticationRequest> {

    private static final Logger log = LoggerFactory.getLogger(RedisSaml2AuthenticationRequestRepository.class);

    private final StringRedisTemplate redis;
    private final RelyingPartyRegistrationRepository registrations;
    private final SamlSpProperties.Redis cfg;

    public RedisSaml2AuthenticationRequestRepository(
            StringRedisTemplate redis, RelyingPartyRegistrationRepository registrations, SamlSpProperties.Redis cfg) {
        this.redis = redis;
        this.registrations = registrations;
        this.cfg = cfg;
    }

    @Override
    public void saveAuthenticationRequest(
            AbstractSaml2AuthenticationRequest authenticationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (authenticationRequest == null) {
            removeAuthenticationRequest(request, response);
            return;
        }
        String key = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue()
                .set(redisKey(key), Saml2AuthnRequestCodec.encode(toData(authenticationRequest)), cfg.getTtl());
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(key).toString());
        if (log.isDebugEnabled()) {
            // 사용자 제어 값(쿠키/samlRequest/relayState)은 로깅하지 않는다. 설정 출처인 registrationId 만.
            log.debug(
                    "SAML AuthnRequest saved to redis (registrationId={}, ttl={}s)",
                    authenticationRequest.getRelyingPartyRegistrationId(),
                    cfg.getTtl().toSeconds());
        }
    }

    @Override
    public AbstractSaml2AuthenticationRequest loadAuthenticationRequest(HttpServletRequest request) {
        String key = readCookie(request);
        if (key == null) {
            return null;
        }
        return rebuild(redis.opsForValue().get(redisKey(key)));
    }

    @Override
    public AbstractSaml2AuthenticationRequest removeAuthenticationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        String key = readCookie(request);
        if (key == null) {
            return null;
        }
        // 원자적 1회 소비: getAndDelete 로 재사용/재생 공격을 차단한다.
        String raw = redis.opsForValue().getAndDelete(redisKey(key));
        response.addHeader(HttpHeaders.SET_COOKIE, expireCookie().toString());
        return rebuild(raw);
    }

    // ---------------------------------------------------------------- helpers

    private String redisKey(String key) {
        return cfg.getKeyPrefix() + key;
    }

    private Data toData(AbstractSaml2AuthenticationRequest req) {
        String binding = (req.getBinding() == Saml2MessageBinding.REDIRECT) ? "REDIRECT" : "POST";
        String sigAlg = null;
        String signature = null;
        if (req instanceof Saml2RedirectAuthenticationRequest redirect) {
            sigAlg = redirect.getSigAlg();
            signature = redirect.getSignature();
        }
        return new Data(
                binding,
                req.getSamlRequest(),
                req.getRelayState(),
                req.getAuthenticationRequestUri(),
                req.getRelyingPartyRegistrationId(),
                req.getId(),
                sigAlg,
                signature);
    }

    private AbstractSaml2AuthenticationRequest rebuild(String raw) {
        Data data = Saml2AuthnRequestCodec.decode(raw);
        if (data == null) {
            return null;
        }
        String rpId = data.relyingPartyRegistrationId();
        if (rpId == null) {
            log.debug("Stored SAML AuthnRequest has no relyingPartyRegistrationId; cannot rebuild");
            return null;
        }
        RelyingPartyRegistration reg = registrations.findByRegistrationId(rpId);
        if (reg == null) {
            log.debug("No RelyingPartyRegistration for stored id; treating as no stored request");
            return null;
        }
        if (data.isRedirect()) {
            Saml2RedirectAuthenticationRequest.Builder b =
                    Saml2RedirectAuthenticationRequest.withRelyingPartyRegistration(reg)
                            .samlRequest(data.samlRequest())
                            .relayState(data.relayState())
                            .authenticationRequestUri(data.authenticationRequestUri())
                            .sigAlg(data.sigAlg())
                            .signature(data.signature());
            if (data.id() != null) {
                b.id(data.id());
            }
            return b.build();
        }
        Saml2PostAuthenticationRequest.Builder b = Saml2PostAuthenticationRequest.withRelyingPartyRegistration(reg)
                .samlRequest(data.samlRequest())
                .relayState(data.relayState())
                .authenticationRequestUri(data.authenticationRequestUri());
        if (data.id() != null) {
            b.id(data.id());
        }
        return b.build();
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (cfg.getCookieName().equals(c.getName())) {
                String v = c.getValue();
                return (v == null || v.isBlank()) ? null : v;
            }
        }
        return null;
    }

    private ResponseCookie buildCookie(String key) {
        return ResponseCookie.from(cfg.getCookieName(), key)
                .httpOnly(true)
                .secure(cfg.isCookieSecure())
                .sameSite(cfg.getCookieSameSite())
                .path(cfg.getCookiePath())
                .maxAge(cfg.getTtl())
                .build();
    }

    private ResponseCookie expireCookie() {
        return ResponseCookie.from(cfg.getCookieName(), "")
                .httpOnly(true)
                .secure(cfg.isCookieSecure())
                .sameSite(cfg.getCookieSameSite())
                .path(cfg.getCookiePath())
                .maxAge(0)
                .build();
    }
}
