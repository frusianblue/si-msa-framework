# INSTALL — framework-idempotency 드롭인

이 zip은 **기존 소스를 건드리지 않는 추가(additive) 패키지**입니다. 압축을 `si-msa-framework/`
루트에서 풀면 폴더가 그대로 병합됩니다.

## 무엇이 들어가나 (전부 신규 / 단 1개만 교체)

| 경로 | 종류 | 영향 |
|---|---|---|
| `framework/framework-idempotency/**` | **신규 폴더** | 기존 파일과 충돌 없음 |
| `settings.gradle` | **교체(기존+1줄)** | `include 'framework:framework-idempotency'` 한 줄만 추가, 나머지 100% 동일 |
| `docs/FRAMEWORK_MODULES.md` | 신규 문서 | 전체 모듈 설계서(참고용) |

> 기존 `services/*`, `framework/framework-core|security|...` 의 **소스는 한 글자도 바뀌지 않습니다.**
> 그래서 기존 코드가 깨질 수 없습니다. 모듈은 아래 "켜기"를 하기 전까지 **휴면**(빈 미등록, 비용 0)입니다.

## 설치 (3단계)

1. **압축 해제** — `si-msa-framework/` 루트에서:
   ```bash
   unzip framework-idempotency-dropin.zip -d /path/to/si-msa-framework
   ```
   (`settings.gradle` 덮어쓰기 확인이 뜨면 Yes — 아래 검증으로 안전 확인)

2. **settings.gradle 검증(선택)** — 1줄만 늘었는지 확인:
   ```bash
   git diff settings.gradle
   # +include 'framework:framework-idempotency'  ... 한 줄만 보이면 정상
   ```
   직접 덮어쓰기가 꺼려지면 zip의 settings.gradle은 무시하고, 기존 파일의 `framework-file-s3`
   include 아래에 위 한 줄만 손으로 추가해도 됩니다.

3. **빌드 확인**:
   ```bash
   ./gradlew :framework:framework-idempotency:compileJava
   ./gradlew spotlessApply        # 포맷 정렬(팀 컨벤션 일치)
   ```

## 켜기 (실제 사용할 서비스에서만)

사용할 서비스(예: user-service) `build.gradle`:
```gradle
dependencies { implementation project(':framework:framework-idempotency') }
```
해당 서비스 `application.yml`:
```yaml
framework:
  idempotency:
    enabled: true
    store: { type: redis }   # 운영(replicas>=2)은 redis 필수. 로컬은 생략 시 memory
```
컨트롤러:
```java
@PostMapping("/api/v1/transfers")
@Idempotent
public ApiResponse<Void> transfer(@RequestBody TransferRequest req) { ... }
```

## 끄기 / 되돌리기
- 끄기: `framework.idempotency.enabled: false` (또는 의존성 제거).
- 완전 제거: `framework/framework-idempotency` 폴더 삭제 + `settings.gradle`의 그 한 줄 삭제.
  기존 코드에 잔재가 남지 않습니다.

## 버전 카탈로그
새로 추가한 외부 라이브러리가 없어 `gradle/libs.versions.toml` / `STACK.md` 변경은 불필요합니다.
(spring-web, data-redis 는 Boot BOM 이 버전 관리)
