# INSTALL — framework-i18n 드롭인

기존 소스를 건드리지 않는 **추가(additive)** 패키지. `si-msa-framework/` 루트에서 압축을 풀면 병합된다.

## 무엇이 들어가나 (전부 신규)
| 경로 | 종류 |
|---|---|
| `framework/framework-i18n/**` | 신규 폴더(충돌 없음) |

> 이번엔 `settings.gradle` 을 **덮어쓰지 않는다.** 지난 idempotency 등록 줄을 보존하기 위해,
> 등록은 아래 "한 줄 추가"로 직접 한다(멱등 스니펫 제공). 기존 `services/*`·`framework/*` 소스는 변경 없음.

## 설치
1. 압축 해제 — 루트에서:
   ```bash
   unzip framework-i18n-dropin.zip -d /path/to/si-msa-framework
   ```
2. **settings.gradle 에 모듈 등록 (한 줄)** — 아래 멱등 스니펫이 안전(이미 있으면 건너뜀, idempotency 줄 보존):
   ```bash
   cd /path/to/si-msa-framework
   grep -q "framework:framework-i18n" settings.gradle || \
     sed -i "/include 'framework:framework-file-s3'/a include 'framework:framework-i18n'           // 선택형: i18n(메시지 외부화/다국어)" settings.gradle
   git diff settings.gradle    # framework-i18n 한 줄만 늘었는지 확인
   ```
   직접 추가해도 됨 — `framework` 묶음 아무 곳에:
   ```gradle
   include 'framework:framework-i18n'
   ```
3. 빌드 확인:
   ```bash
   ./gradlew :framework:framework-i18n:compileJava
   ./gradlew spotlessApply
   ```

## 켜기 (사용할 서비스에서만)
`build.gradle`:
```gradle
dependencies { implementation project(':framework:framework-i18n') }
```
`application.yml`:
```yaml
framework:
  i18n:
    enabled: true
    default-locale: ko
    error-localization: true
```
자세한 사용법은 `framework/framework-i18n/README.md` 참고.

## 끄기 / 되돌리기
- 끄기: `framework.i18n.enabled: false` 또는 의존성 제거.
- 제거: `framework/framework-i18n` 폴더 + `settings.gradle` 의 그 한 줄 삭제.
