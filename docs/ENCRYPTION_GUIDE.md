# ENCRYPTION_GUIDE.md — 암호화 값 다루기 (설정값 · 컬럼 · 파일)

> 민감한 값을 **평문으로 저장소/설정에 남기지 않기** 위한 공통 암호화 체계. 모든 서비스 공통이며, 각 서비스
> `README.md` 의 "암호화 값 다루기" 절이 이 문서를 가리킨다.

암호화 경로는 **3가지**이고, 모두 같은 마스터 키(`AES_SECRET`)를 신뢰 기반으로 한다.

| # | 경로 | 마커/형식 | 누가/언제 | 알고리즘 | 대표 사용처 |
|---|---|---|---|---|---|
| ① | **설정값 암호화** | `ENC(...)` | `EncryptedPropertyEnvironmentPostProcessor` 가 **기동 시 자동 복호** | AES-256-GCM, Base64(IV‖ct+tag) | `application*.yml`/env 의 DB 비밀번호·외부 API 키 등 |
| ② | **컬럼/도메인 암호화** | `enc:` | cipher 빈(`AesSigningKeyCipher` 등)이 **쓰기 시 자동 암호화, 읽기 시 마커 인지** | AES-256-GCM, `enc:`+Base64(IV‖ct+tag) | AS 서명키 개인키(`auth_signing_key.jwk_json`) |
| ③ | **파일 본문 at-rest** | (헤더 IV) | `framework-file` 저장/조회 스트림 | AES-256-CBC, IV(16B)‖ct | 업로드 파일 암호화 저장 |

> ⚠️ **`ENC(...)` 와 `enc:` 는 다른 마커**다. 전자는 *설정 프로퍼티* 전용(대문자 괄호형), 후자는 *DB 컬럼/도메인 값* 전용(소문자 콜론형). 혼동 금지.

핵심 구현: `framework-core` 의 `com.company.framework.core.crypto.*`
(`AesCryptoService` · `CryptoCli` · `EncryptedPropertyEnvironmentPostProcessor` · `DecryptingPropertySource` · `AesMasterKeySafetyGuard` · `CryptoProperties`).

---

## 1. 마스터 키 — `AES_SECRET` (모든 암호화의 신뢰 기반)

- 설정 키: `framework.crypto.aes-secret`, 운영 주입 변수: **`AES_SECRET`**.
- 임의 문자열을 받아 **SHA-256 으로 256-bit AES 키를 파생**한다(따라서 길이 자체보다 **엔트로피**가 중요).
- **생성(권장)**:
  ```bash
  openssl rand -base64 32      # 강한 32바이트 키
  ```
- **주입**:
  - 운영: **k8s Secret → 환경변수 `AES_SECRET`**. 코드/Git/로그/셸 히스토리에 절대 남기지 않는다.
  - yaml 에는 `aes-secret: ${AES_SECRET}` 형태로 **주입 지점만** 둔다(값 미기재).
  - 로컬: 기본 placeholder 가 있으나 **경고**가 뜬다(아래 안전장치).
- **절대 교체 금지 원칙**: 키를 바꾸면 그 키로 만든 **기존 암호문(`ENC(...)`·`enc:` 컬럼)을 전부 복호할 수 없다**. 키 회전이 필요하면 *기존 값 재암호화 마이그레이션*을 반드시 동반한다(읽기는 마커 인지라 평문↔암호문 혼재는 안전하지만, 키가 바뀌면 옛 암호문은 못 푼다).

### 운영 안전장치 — `AesMasterKeySafetyGuard`

`prod`/`production` 프로파일에서 마스터 키가 아래 중 하나면 **부팅을 실패**시킨다(사고 차단). 비-prod(local/dev)는 경고 배너만.

- 키가 비어 있음
- 레포 기본 placeholder(`change-me-please-set-framework-crypto-aes-secret`) 또는 `change-me`/`change-this` 류 미교체 흔적
- 16바이트 미만(엔트로피 부족)

---

## 2. ① 설정값 암호화 — `ENC(...)`

### 2.1 동작 (자동 복호화)

- `application*.yml`/env/시스템 프로퍼티의 값이 `ENC(...)` 면, `EncryptedPropertyEnvironmentPostProcessor`(EPP)가 **컨텍스트 생성 이전**에 마스터 키로 복호화한다.
- 토글: `framework.crypto.config-decryption.enabled` (**기본 `true`**). 끄면 무동작.
- **무동작 조건**: 어떤 소스에도 `ENC(...)` 가 없으면 마스터 키 없이도 그냥 통과한다(부작용 0).
- **실패 정책(fail-fast)**:
  - `ENC(...)` 가 있는데 마스터 키가 없으면 → 기동 실패.
  - **마스터 키 자신은 `ENC(...)` 로 둘 수 없다**(닭-달걀) → `AES_SECRET` 은 평문/시크릿으로 주입.
  - 복호화 실패(GCM 인증 실패=값 변조/키 불일치) → 기동 실패. (예외/로그에 평문·키 노출 안 함.)

### 2.2 `ENC(...)` 토큰 만드는 법

암호화 엔드포인트를 HTTP/빈으로 노출하지 않는다(키 노출 위험). **CLI** `CryptoCli` 로 만든다.

**(권장) Gradle 태스크** — 루트에서:
```bash
# 키는 환경변수로(셸 히스토리에 안 남김). 데몬 env 격리 회피 위해 --no-daemon.
AES_SECRET='실제마스터키' ./gradlew --no-daemon -q \
  :framework:framework-core:encryptSecret -Pplain='sipass1234'
# 출력 예: ENC(Qk9kZ2c4...base64...)
```
> 데몬을 끄기 싫으면 키도 프로퍼티로 넘길 수 있으나(`-PAES_SECRET='...'`) **셸 히스토리/프로세스 인자에 노출**되니 일회성/개발용만.
> ```bash
> ./gradlew -q :framework:framework-core:encryptSecret -PAES_SECRET='실제마스터키' -Pplain='sipass1234'
> ```

**(대안) 순수 java** — jar 빌드 후:
```bash
AES_SECRET='실제마스터키' \
  java -cp framework/framework-core/build/libs/framework-core-*.jar \
  com.company.framework.core.crypto.CryptoCli encrypt 'sipass1234'
```

> GCM 은 IV 가 매번 랜덤이라 **같은 평문도 매번 다른 `ENC(...)`** 가 나온다(정상, 둘 다 유효).

### 2.3 yaml 에 넣기

```yaml
spring:
  datasource:
    password: ENC(Qk9kZ2c4...base64...)   # 기동 시 자동 복호 → 실제 비번으로 해석
```
- 마스터 키만 평문으로 주입돼 있으면 된다: `framework.crypto.aes-secret: ${AES_SECRET}`.
- **운영 정책(권장)**: prod 비밀은 가능하면 **env/시크릿 직접 주입**을 유지하고, `ENC(...)` 는 *dev 편의 + 저장소에 평문이 남지 않게* 하는 용도로 쓴다.

---

## 3. ② 컬럼/도메인 암호화 — `enc:`

### 3.1 동작

- DB 컬럼 등 도메인 값을 cipher 빈이 **쓰기 시 자동 암호화**(`enc:`+Base64), **읽기 시 마커 인지** 후 복호한다.
- 대표 구현: `services/auth-server` 의 `AesSigningKeyCipher`(서명키 개인키 `jwk_json` 보호) — `AesCryptoService` 재사용.
- 토글(쓰기): `auth-server.signing-key.encryption.enabled` (**기본 on**). **읽기는 토글과 무관하게 항상 마커 인지** → 평문(레거시/데모)↔암호문 **혼재·롤백 안전**.

### 3.2 새 값을 컬럼 암호화하려면

- `AesSigningKeyCipher` 와 같은 패턴으로 cipher(예: `protect(plain)` → `enc:...`, `reveal(stored)` → plain)를 만들어 쓰기/읽기 양쪽에 끼운다.
- 마스터 키는 동일하게 `AesCryptoService`(= `framework.crypto.aes-secret`)를 쓴다.
- **KMS/Vault 로 이관**: cipher **빈만 교체**하면 된다(도메인/스키마 무변경).

---

## 4. ③ 파일 본문 at-rest (참고)

- `AesCryptoService.encryptingInputStream/decryptingInputStream` — AES-CBC, 출력 `IV(16B)‖ciphertext`(대용량 스트리밍). 기밀성 제공(무결성 태그 없음 — 파일 메타는 DB 로 별도 관리).
- S3 등 content-length 가 필요한 백엔드는 `AesCryptoService.cbcEncryptedLength(plainLen)` 로 정확한 크기를 계산해 넘긴다.
- 사용처: `framework-file` 저장소 계열. 토글/설정은 `framework.file.*`.

---

## 5. 운영 체크리스트 / 금지사항

**해라**
- `AES_SECRET` 은 `openssl rand -base64 32` 로 만든 강한 키를 **k8s Secret → env** 로 주입.
- 비밀은 `ENC(...)`(설정) / `enc:`(컬럼)로 저장소에 평문 미잔존.
- prod 배포 전 `AesMasterKeySafetyGuard` 경고/차단을 확인(약한 키면 prod 부팅 실패).

**하지 마라**
- 마스터 키를 **교체**(기존 암호문 전부 복호 불가 — 재암호화 마이그레이션 없이는 금지).
- 마스터 키를 `ENC(...)` 로 두기(닭-달걀, 기동 실패).
- 키/평문을 셸 히스토리·로그·소스·이미지 레이어에 남기기.
- `ENC(...)` 와 `enc:` 마커 혼동(설정 vs 컬럼).

---

## 6. 알고리즘 요약

| 용도 | 변환 | 출력 |
|---|---|---|
| 문자열(설정/컬럼) | `AES/GCM/NoPadding`, IV 12B, tag 128b | Base64(IV‖ct+tag) (+ 컬럼은 `enc:` 접두) |
| 스트림(파일) | `AES/CBC/PKCS5Padding`, IV 16B | IV(16B)‖ct |
| 키 파생 | 입력 시크릿 → `SHA-256` → 256-bit AES 키 | — |

---

## 참고
- 설정 EPP 설계 함정: `HANDOFF.md` §6 "설정값 암호화 EPP 완료" 항목.
- AS 서명키 개인키 암호화: `docs/modules/AUTH_SERVER.md` §3/§6 · `docs/NEXT_SIGNING_KEY_ROTATION.md`.
