# INSTALL — framework-client 드롭인

기존 소스를 건드리지 않는 **추가(additive)** 패키지. `si-msa-framework/` 루트에서 압축을 풀면 병합된다.

## 무엇이 들어가나 (전부 신규)
| 경로 | 종류 |
|---|---|
| `framework/framework-client/**` | 신규 폴더(충돌 없음) |

> `settings.gradle` 은 덮어쓰지 않는다 — 아래 멱등 스니펫으로 한 줄만 추가(기존 모듈 등록 보존).
> 기존 `services/*`·`framework/*` 소스 변경 없음. 새 외부 의존성 없음.

## 설치
1. 압축 해제 — 루트에서:
   ```bash
   unzip framework-client-dropin.zip -d /path/to/si-msa-framework
   ```
2. settings.gradle 에 모듈 등록(이미 있으면 건너뜀):
   ```bash
   cd /path/to/si-msa-framework
   grep -q "framework:framework-client" settings.gradle || \
     sed -i "/include 'framework:framework-file-s3'/a include 'framework:framework-client'          // 선택형: 외부 API 표준 호출" settings.gradle
   git diff settings.gradle    # framework-client 한 줄만 늘었는지 확인
   ```
3. 빌드 확인:
   ```bash
   ./gradlew :framework:framework-client:compileJava
   ./gradlew spotlessApply
   ```

## 켜기 (사용할 서비스에서만)
`build.gradle`:
```gradle
dependencies { implementation project(':framework:framework-client') }
```
`application.yml`:
```yaml
framework:
  client:
    enabled: true
    connect-timeout: 2s
    read-timeout: 5s
```
사용법(주입/호출/서킷·재시도 동작)은 `framework/framework-client/README.md` 참고.

## 끄기 / 되돌리기
- 끄기: `framework.client.enabled:false` 또는 의존성 제거. 기능별 토글도 가능(retry/circuit-breaker/logging/trace).
- 제거: `framework/framework-client` 폴더 + `settings.gradle` 의 그 한 줄 삭제.
