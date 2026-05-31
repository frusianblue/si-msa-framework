# INSTALL — framework-idgen 드롭인

기존 소스를 건드리지 않는 **추가(additive)** 패키지. `si-msa-framework/` 루트에서 압축을 풀면 병합된다.

## 무엇이 들어가나 (전부 신규)
| 경로 | 종류 |
|---|---|
| `framework/framework-idgen/**` | 신규 폴더(충돌 없음) |

> `settings.gradle` 은 덮어쓰지 않는다 — 아래 멱등 스니펫으로 한 줄만 추가(기존 idempotency/i18n 등록 보존).
> 기존 `services/*`·`framework/*` 소스 변경 없음.

## 설치
1. 압축 해제 — 루트에서:
   ```bash
   unzip framework-idgen-dropin.zip -d /path/to/si-msa-framework
   ```
2. settings.gradle 에 모듈 등록(이미 있으면 건너뜀):
   ```bash
   cd /path/to/si-msa-framework
   grep -q "framework:framework-idgen" settings.gradle || \
     sed -i "/include 'framework:framework-file-s3'/a include 'framework:framework-idgen'           // 선택형: 공통 채번(Snowflake/업무코드)" settings.gradle
   git diff settings.gradle    # framework-idgen 한 줄만 늘었는지 확인
   ```
3. 빌드 확인:
   ```bash
   ./gradlew :framework:framework-idgen:compileJava
   ./gradlew spotlessApply
   ```

## 켜기 (사용할 서비스에서만)
`build.gradle`:
```gradle
dependencies { implementation project(':framework:framework-idgen') }
```
`application.yml`:
```yaml
framework:
  idgen:
    enabled: true
    sequence: { table-name: id_sequence, initialize: true, default-pad: 6 }
```
사용법/운영 주의(Snowflake node-id)는 `framework/framework-idgen/README.md` 참고.

## 끄기 / 되돌리기
- 끄기: `framework.idgen.enabled:false` 또는 의존성 제거.
- 제거: `framework/framework-idgen` 폴더 + `settings.gradle` 의 그 한 줄 삭제.
