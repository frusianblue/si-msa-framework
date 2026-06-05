# SonarQube 사용 가이드 — si-msa-framework

> **누가 보나**: 코드 품질·보안약점 게이트를 운용하는 사람(빌드/DevOps/리드). 정적분석(버그·코드스멜·보안 핫스팟)과 커버리지를 한곳에서 본다. 금융/공공 SI 에서 요구하는 SW 보안약점 진단·품질 지표의 상시 측정 기반.

## 0. 한 줄 요약 — "배선은 끝났고, 서버만 붙이면 된다"

이 프레임워크는 SonarQube **연동이 이미 다 되어 있다**. 빠진 건 단 하나 — **분석 결과를 받을 Sonar 서버**다. 서버 주소와 토큰만 주면 `./gradlew sonar` 한 줄로 돈다.

이미 들어가 있는 것:
- 루트 `build.gradle` — `org.sonarqube` 플러그인 + `sonar { }` 블록(projectKey `si-msa-framework`, UTF-8, Java 21).
- **JaCoCo → Sonar 연동** — 각 모듈 `jacocoTestReport` 가 XML 을 내고, `sonar.coverage.jacoco.xmlReportPaths` 글롭이 전 모듈 XML 을 수집(커버리지가 Sonar 에 그대로 올라감).
- **CI 양쪽** — GitHub Actions(`deploy/cicd/ci-cd.yml`)·Jenkins(`deploy/cicd/Jenkinsfile`) 모두 게이트에 `./gradlew sonar` 스테이지가 있고 `SONAR_HOST_URL`/`SONAR_TOKEN` 을 환경변수로 받는다.
- 플러그인 버전은 `gradle/libs.versions.toml` 의 `sonarqube` 로 고정.

> 즉 "한 번도 안 써본" 이유는 코드 문제가 아니라, **가리킬 서버가 없었던 것**뿐이다. 아래대로 서버를 띄우고 토큰을 넣으면 끝난다.

---

## 1. 분석 서버 고르기 (2026 기준 에디션 정리)

| 에디션 | 무엇 | 비용 | 브랜치/PR 분석 | 우리 용도 |
|---|---|---|---|---|
| **SonarQube Community Build** | 자체 호스팅 무료판(구 Community Edition). 도커 이미지 `sonarqube:community` | 무료(LGPL v3) | ✕ (main 한 갈래만) | **로컬·소규모 자체 서버** 시작점 |
| **SonarQube Server** (Developer/Enterprise/DC) | 자체 호스팅 상용. 현재 LTA = **2026.1** | 유료 | ✅ (PR 데코·브랜치) | 운영 전사 게이트(PR 차단까지) |
| **SonarQube Cloud** | SaaS(구 SonarCloud) | 유료(OSS 무료티어) | ✅ | 서버 운영 부담 없이 |

**판단**: 우선 **Community Build** 로 로컬에서 한 번 돌려 결과를 보고, 운영 게이트에서 PR 단위 차단(신규코드 기준)이 필요하면 Server(Developer+) 또는 Cloud 로 승격. 우리 CI 는 `push`/PR 양쪽에서 게이트를 도는데, **PR 데코레이션·브랜치 분석은 Developer Edition 이상 기능**이라 Community Build 에서는 main 분석만 됨을 유의(아래 6장).

---

## 2. 로컬에 Community Build 서버 띄우기 (가장 빠른 길)

데모/체험은 임베디드 H2 로 충분하다(운영은 외부 DB 필수 — 4장).

```bash
# 1) 서버 기동 (포트 9000, 임베디드 H2 — 평가 전용)
docker run -d --name sonarqube -p 9000:9000 sonarqube:community

# 2) 기동 확인 (GREEN 뜰 때까지 1~2분)
curl -s http://localhost:9000/api/system/status   # {"status":"UP"} 면 준비됨
```

- 브라우저 `http://localhost:9000` → 초기 로그인 `admin / admin` → 비밀번호 변경.
- ⚠️ 임베디드 H2 는 **평가 전용**. 데이터 보존·운영은 PostgreSQL 등 외부 DB 로(4장).
- Apple Silicon(arm64)·amd64 모두 지원.

### 토큰 발급
SonarQube UI → **My Account → Security → Generate Tokens** → 타입 **"Global Analysis Token"**(또는 프로젝트 토큰) 생성 → 값 복사(다시 못 봄).

---

## 3. 분석 실행 (쓰는 법)

### 핵심 순서 — 커버리지를 같이 올리려면 *테스트·JaCoCo 먼저*
Sonar 는 코드를 다시 컴파일하지 않고 **이미 만들어진 산출물**(클래스, JaCoCo XML)을 읽는다. 그래서 `sonar` 단독 실행은 커버리지 0% 로 나온다. 반드시 테스트→리포트→sonar 순서:

```bash
# 전 모듈 테스트 + JaCoCo XML 생성 + Sonar 업로드 (한 줄)
./gradlew test jacocoTestReport sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=<발급한_토큰>
```

환경변수로도 동일(플러그인이 `SONAR_HOST_URL`/`SONAR_TOKEN` 을 자동 인식 — CI 가 이 방식):

```bash
export SONAR_HOST_URL=http://localhost:9000
export SONAR_TOKEN=<발급한_토큰>
./gradlew test jacocoTestReport sonar
```

> `sonar.login`(구) 은 deprecated → **`sonar.token`** 사용. 분석이 끝나면 콘솔 끝에 `ANALYSIS SUCCESSFUL, you can find the results at: http://localhost:9000/dashboard?id=si-msa-framework` 링크가 뜬다.

### 결과 보기
대시보드에서: **Bugs / Vulnerabilities / Security Hotspots / Code Smells / Coverage / Duplications**. 금융·공공 맥락에서 특히 **Security Hotspots**(검토 필요 지점)와 **Vulnerabilities** 를 우선 본다. OWASP 의존성 취약점은 별도(`./gradlew dependencyCheckAggregate`)이고, Sonar 는 **소스 코드** 약점을 본다 — 둘은 상호보완.

---

## 4. 운영 서버(외부 DB) — 데이터 보존이 필요해지면

임베디드 H2 는 컨테이너를 지우면 사라진다. 운영/팀 공용은 PostgreSQL + 볼륨:

```yaml
# docker-compose.yml (요지)
services:
  db:
    image: postgres:16
    environment: { POSTGRES_USER: sonar, POSTGRES_PASSWORD: sonar, POSTGRES_DB: sonar }
    volumes: [ "sonar_db:/var/lib/postgresql/data" ]
  sonarqube:
    image: sonarqube:community          # 또는 운영은 sonarqube:2026-lta-* (Server 에디션)
    depends_on: [ db ]
    environment:
      SONAR_JDBC_URL: jdbc:postgresql://db:5432/sonar
      SONAR_JDBC_USERNAME: sonar
      SONAR_JDBC_PASSWORD: sonar
    ports: [ "9000:9000" ]
    volumes:
      - "sonar_data:/opt/sonarqube/data"
      - "sonar_logs:/opt/sonarqube/logs"
      - "sonar_extensions:/opt/sonarqube/extensions"
volumes: { sonar_db: , sonar_data: , sonar_logs: , sonar_extensions: }
```

- 리눅스 호스트는 ES 요구로 `vm.max_map_count=524288`, `fs.file-max=131072` 설정 필요(`sysctl -w vm.max_map_count=524288`).
- 운영 상용(Server)은 LTA 태그(예 `2026-lta-developer`) 사용. ⚠️ 한 DB 스키마에는 **하나의 인스턴스만** 연결(동시 연결 시 데이터 손상).

---

## 5. CI 에서 켜기 — 시크릿만 넣으면 끝

스테이지는 이미 있다. 비밀값만 등록하면 동작한다.

**GitHub Actions** (`deploy/cicd/ci-cd.yml` 의 `SonarQube analysis` 스텝, 현재 `push` 한정):
저장소 **Settings → Secrets and variables → Actions** 에 추가
- `SONAR_HOST_URL` = 사내 Sonar 서버 주소
- `SONAR_TOKEN` = Global Analysis Token

**Jenkins** (`Jenkinsfile` 의 `withSonarQubeEnv('sonar')`, `main` 한정):
- **Manage Jenkins → System → SonarQube servers** 에 이름 **`sonar`** 로 서버 URL + 인증 토큰 등록(스테이지가 이 이름을 참조).

> `fetch-depth: 0`(전체 히스토리)이 이미 설정돼 있다 — Sonar 의 신규코드/blame 산정에 필요.

---

## 6. Quality Gate 를 "진짜 게이트"로 (권장 — 현재는 업로드만 함)

지금 CI 는 분석을 **올리기만** 하고, 품질 게이트 실패로 **머지를 막지는 않는다**. 신규코드 기준 게이트(예: 신규 커버리지 80%, 신규 보안취약점 0)로 막으려면:

**Gradle(GitHub Actions 등)** — `sonar` 호출에 한 옵션 추가:
```bash
./gradlew test jacocoTestReport sonar -Dsonar.qualitygate.wait=true
```
→ 게이트 실패 시 빌드가 non-zero 로 종료되어 스텝이 실패(=PR 차단).

**Jenkins** — 분석 직후 별도 스테이지:
```groovy
stage('Quality Gate') {
  steps { timeout(time: 10, unit: 'MINUTES') { waitForQualityGate abortPipeline: true } }
}
```
(웹훅 또는 폴링으로 게이트 결과를 받아 실패 시 파이프라인 중단.)

> ⚠️ 게이트 차단은 파이프라인 동작을 바꾸는 결정이라 이 가이드는 **방법만** 적어둔다. 실제 CI 파일에 반영할지는 팀 합의 후. 초반엔 게이트를 느슨하게(신규코드 위주) 잡고 점진적으로 조인다 — JaCoCo 커버리지 게이트(`build.gradle` 의 주석 처리된 `jacocoTestCoverageVerification`)와 같은 철학.

**Community Build 한계**: 브랜치·PR 분석이 없어 main 한 갈래만 측정된다. PR 단위 신규코드 게이트가 필요하면 **Server(Developer+)** 또는 **Cloud** 로 승격(이때 CI 의 PR 분석이 의미를 가진다).

---

## 7. 끄는 법 / 건너뛰기

- 특정 빌드에서만 제외: `sonar` 태스크를 호출하지 않으면 됨(`./gradlew build` 에는 안 묶여 있음).
- CI 에서 임시 비활성: 해당 스테이지 `if:`/`when` 조건을 끄거나 시크릿 미설정 시 스킵되도록.
- 완전 제거(비권장): 루트 `build.gradle` 의 플러그인 `alias(libs.plugins.sonarqube)` 와 `sonar { }` 블록, CI 스테이지 제거. 단 커버리지(JaCoCo)는 Sonar 와 독립이라 남겨도 무방.

---

## 8. 트러블슈팅

| 증상 | 원인 | 해결 |
|---|---|---|
| **커버리지 0%** 로 표시 | `sonar` 만 단독 실행(JaCoCo XML 없음) | `test jacocoTestReport` 를 **먼저**(또는 같은 호출에) 실행 |
| `Not authorized` / 401 | 토큰 누락·만료·권한 부족 | `SONAR_TOKEN` 재발급(Global Analysis Token), `sonar.token` 으로 전달 |
| `Fail to get bootstrap index ... Connection refused` | 서버 미기동·URL 오타 | `curl $SONAR_HOST_URL/api/system/status` 가 `UP` 인지 확인 |
| 서버가 `STARTING` 에서 안 올라옴 | ES `vm.max_map_count` 부족 | `sysctl -w vm.max_map_count=524288` |
| 신규 모듈이 커버리지 합산에 안 보임 | 루트 `build.gradle` 의 `jacocoAggregation` 목록 누락 | 모듈 추가 시 `settings.gradle` include + 이 목록 + (필요시) `sonar` 글롭 확인 |
| arm64(Mac) 에서 이미지 안 뜸 | 구버전 이미지 | 최신 `sonarqube:community` 사용(arm64 지원) |

---

## 관련 문서
- CI/CD 전체 흐름: [`K8S_CICD_MULTISERVICE.md`](K8S_CICD_MULTISERVICE.md) §5 (게이트는 레포 전체 1회)
- 커버리지 집계 산출물: `./gradlew testCodeCoverageReport` → `build/reports/jacoco/testCodeCoverageReport/`
- 포맷 게이트(Spotless)·의존성 취약점(OWASP) — 같은 "Quality Gates" 묶음. 상세는 루트 `build.gradle` 주석.
