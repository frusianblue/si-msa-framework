# NEXT_ADMIN_CRASHLOOP_FILESTORAGE.md — admin-service CrashLoop 원인 규명 + 해결

> ✅✅ **완료(2026-06-08)** — admin/user 모두 해소, promote 재완주로 8파드 1/1 Running 실측 PASS.
> `42-diagnose-admin-file.sh` 가 admin 파드 env `/tmp/uploads` 주입 + file 에러 미재현 확인. 누적 정본은 `../HANDOFF.md` §7.
> (완료 설계서 규약상 다음 정리 시 `docs/archive/` 이동 후보.)
>
> 인계 과제(HANDOFF_SUMMARY "바로 다음 할 일 1"): admin-service CrashLoop 원인 규명.
> 결론: **file storage base-path 환경변수 바인딩 실패**(kebab-case relaxed binding 함정). 서비스 설정(application.yml) 명시 placeholder + user configmap type=local 로 해소.

---

## 1. 원인 (확정)

admin-service 는 `framework-file` 을 의존하고(`build.gradle`), `framework-file-s3` 는 주석 → 저장소 type 기본값 `local`.
admin configmap 에는 `FILE_STORAGE_TYPE` 키가 없으므로 admin 의 `framework.file.storage.type` 은 **local** 로 확정된다.

`FileStorageAutoConfiguration` 은 `framework.file.enabled=true`(기본) + `type=local`(기본) 조건에서
`localFileStorage` 빈을 만들고, 그 생성자 `FileSystemFileStorage(basePath, "local")` 가 **기동 시점에**
`Files.createDirectories(basePath)` 를 호출한다. 이어 `fileService`/`fileController` 가 `FileStorage` 빈에 의존한다.

- `framework.file.storage.base-path` 기본값 = `./uploads` → 컨테이너 WORKDIR `/application` 기준으로 `/application/uploads`.
- deployment-hardening 으로 `readOnlyRootFilesystem: true` → `/application` 은 read-only.
- 따라서 `Files.createDirectories(/application/uploads)` 가 `FileSystemException: Read-only file system` 으로 실패
  → `localFileStorage` 빈 생성 실패 → `fileService` 의존성 미충족 → **ApplicationContext 기동 실패(CrashLoop)**.

증거(첨부 로그):
```
Caused by: java.lang.IllegalStateException: 파일 저장 기본경로 생성 실패: /application/uploads
Caused by: java.nio.file.FileSystemException: /application/uploads: Read-only file system
        at ...FileSystemFileStorage.<init>(FileSystemFileStorage.java:30)
```
로그 경로가 `/application/uploads` 라는 점이 핵심 = **base-path 가 기본값 그대로** = 매니페스트가 주려던
`/tmp/uploads` 가 admin 의 `base-path` 프로퍼티에 **바인딩되지 않았다**.

## 2. 바인딩이 실패한 이유 (relaxed binding 함정)

admin 의 application.yml(전 프로파일)에는 `framework.file.*` 가 **전혀 선언되어 있지 않다**.
즉 base-path 는 환경변수 relaxed binding 에만 의존한다.

- 매니페스트(deployment-hardening / overlays dev·local)가 주는 env: `FRAMEWORK_FILE_STORAGE_BASE_PATH`
- Spring Boot canonical→env 변환 규칙(공식): 점→`_`, **대시 제거**, 대문자.
  → `framework.file.storage.base-path` 의 정식 env 는 `FRAMEWORK_FILE_STORAGE_BASEPATH`(BASEPATH, 대시 제거).
- `FRAMEWORK_FILE_STORAGE_BASE_PATH`(BASE_PATH, 언더스코어)는 `framework.file.storage.base.path`(base / path 분리)로
  해석되어 `base-path` 와 어긋난다. (버전에 따라 우연히 매칭되는 보고도 있으나 **보장되지 않음** — 이론에 기대지 않는다.)

추가 정황: user-service 는 같은 프로퍼티를 application.yml 에 **명시 placeholder** `base-path: ${FILE_BASE_PATH:./uploads}`
로 선언해 둔다 → user 는 `FILE_BASE_PATH` 를 기대하므로 매니페스트의 `FRAMEWORK_FILE_STORAGE_BASE_PATH` 와는
또 다른 이름이라 역시 불일치(다만 user 는 type=s3 라 base-path 미사용으로 잠복).

## 3. 해결 (적용)

admin application.yml 의 `framework:` 블록에 file 설정을 **명시 placeholder** 로 추가
(`services/admin-service/src/main/resources/application.yml`):

```yaml
  file:
    enabled: true
    storage:
      type: ${FILE_STORAGE_TYPE:local}
      base-path: ${FRAMEWORK_FILE_STORAGE_BASE_PATH:./uploads}   # 매니페스트 env 명과 정확히 일치
      max-size: 10485760
```

명시 placeholder 는 환경변수명을 그대로 매칭하므로 relaxed binding 의 kebab-case 모호성을 우회한다.
→ 매니페스트의 `FRAMEWORK_FILE_STORAGE_BASE_PATH=/tmp/uploads`(hardening tmp emptyDir, 쓰기 가능)가 확정 바인딩
→ `createDirectories(/tmp/uploads)` 성공 → localFileStorage 빈 생성 → 기동 성공.

> 매니페스트는 **무변경**(이미 `FRAMEWORK_FILE_STORAGE_BASE_PATH` 를 hardening/dev/local 에 주고 있음).
> 변경은 admin 서비스 설정 한 블록뿐.

### 적용 절차
1. drop-in zip 적용(`unzip -o`).
2. admin-service 이미지 재빌드 → promote(불변 sha 핀) → ArgoCD sync.
   - ⚠️ 현재 master 의 `overlays/prod/kustomization.yaml` images 는 sentinel `__GITSHA__` 로 되돌아가 있음
     (41-verify G12 FAIL "sentinel 남음"). promote(40) 재실행으로 핀+commit+push 가 선행되어야 새 RS 가 정상 이미지를 pull 한다.
3. `bash deploy/k8s/prod-kind/42-diagnose-admin-file.sh` 로 검증:
   - 2) env 에 `FRAMEWORK_FILE_STORAGE_BASE_PATH=/tmp/uploads` 주입 PASS
   - 3) 로그 경로가 `/tmp/uploads`(또는 매칭 로그 없음) — `/application/uploads` 면 미반영
   - 4) Running&Ready 파드 존재 PASS

## 4. 잔여 / 별건 (admin 과 분리)

- **user-service s3 타입 잠복 위험**: user configmap `FILE_STORAGE_TYPE=s3` 인데 build.gradle 의 `framework-file-s3`
  는 주석. s3 모듈 없이 type=s3 면 `FileStorage` 빈 자체가 없어(`localFileStorage` 는 type=local 조건) `fileService`
  미충족으로 깨질 수 있다. prod 가 현재 type=s3 유지면 점검 필요. (해결안: s3 모듈 의존 해제 + S3 자격증명, 또는
  리허설은 type=local + base-path /tmp/uploads + env 명 통일.)
- **env 명 통일(권고)**: user 의 `${FILE_BASE_PATH:...}` 를 `${FRAMEWORK_FILE_STORAGE_BASE_PATH:...}` 로 통일하면
  전 서비스가 hardening 공통 env 하나로 제어된다(이름 난립 제거). admin CrashLoop 와 직접 무관하여 본 zip 에는 미포함.
- **prod images sentinel**: §3-2 의 promote 재실행 과제(40-promote).

## 5. PITFALLS 머지용 초안 (curated ledger 에 형님이 머지)

```
### [겪음 encountered] read-only 루트FS + framework-file 기본 local 저장소 → 기동 실패, 그리고 env 가 안 먹는 이유
증상: admin-service CrashLoop. 로그 "파일 저장 기본경로 생성 실패: /application/uploads",
      "Read-only file system" at FileSystemFileStorage.<init>. 매니페스트에 base-path env 를 줬는데도 동일.
원인: (1) framework-file 의존 서비스는 type=local(기본) 시 기동 시점에 base-path 에 createDirectories →
          readOnlyRootFilesystem 루트FS 에서 기본 base-path(./uploads=/application/uploads)는 쓰기 불가.
      (2) 서비스 application.yml 이 base-path 를 선언하지 않으면 env relaxed binding 에 의존하는데,
          kebab-case base-path 의 정식 env 는 대시 제거형 FRAMEWORK_FILE_STORAGE_BASEPATH 이다.
          매니페스트가 준 FRAMEWORK_FILE_STORAGE_BASE_PATH(언더스코어)는 base.path 로 해석되어 매칭 보장 안 됨
          → 기본값 ./uploads 그대로 사용 → 같은 read-only 실패.
해결: application.yml 에 명시 placeholder 로 박는다 — base-path: ${FRAMEWORK_FILE_STORAGE_BASE_PATH:./uploads}.
      명시 placeholder 는 env 명을 그대로 매칭하므로 relaxed binding 모호성을 우회. 경로는 쓰기 가능한
      /tmp/uploads(hardening tmp emptyDir)로. (운영 영속 업로드는 s3/PVC.)
교훈: 매니페스트 env 가 kebab-case 프로퍼티를 겨냥하면 언더스코어 형태를 가정하지 말고 application.yml 에
      명시 placeholder 를 두어 환경변수명을 고정하라. "env 를 줬다 ≠ 프로퍼티에 바인딩됐다."
```
