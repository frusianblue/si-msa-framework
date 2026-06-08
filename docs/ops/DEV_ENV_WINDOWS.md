# Windows 로컬 개발 환경 구성 가이드 — si-msa-framework

소스를 빌드/실행/개발하기 위한 Windows 툴체인. (k8s 클러스터 도구 = `docs/modules/LOCAL_K8S_ENV_SETUP.md`,
클러스터 애드온 = `docs/modules/K8S_ADDONS.md`, 로컬 배포 = `docs/modules/LOCAL_K8S_TEST.md`)

스택 고정값: **Java 21 · Spring Boot 4.0.6 · Spring Cloud 2025.1.1 · Gradle 8.14(래퍼) · Lombok · MapStruct 1.6.3 · Spotless(Palantir Java Format)**.

> **권장 작업 방식**: 빌드/도커/배포 명령은 **WSL2(Ubuntu)** 안에서 돌리는 게 가장 깔끔하다(`./gradlew`, `docker`, `kubectl`, `kind` 가 리눅스에서 매끄럽다). IDE 만 Windows 네이티브로 띄우고 빌드/실행을 WSL2 로 하는 구성을 추천. 아래는 둘 다(네이티브/WSL2) 적는다.

---

## 0. 한눈에

| 프로그램 | 용도 | 필수? | Windows 설치(winget) |
|---|---|---|---|
| JDK 21 (Temurin) | 컴파일/실행 | ✅ | `EclipseAdoptium.Temurin.21.JDK` |
| Git | 소스 클론 | ✅ | `Git.Git` |
| Gradle 8.14 | 빌드 | ❌(래퍼 동봉) | (불필요 — `gradlew.bat` 사용) |
| IntelliJ IDEA | 개발 IDE | ✅(권장) | `JetBrains.IntelliJIDEA.Community` |
| Docker Desktop | 이미지 빌드·로컬 DB/Redis·kind | ✅ | (공식 설치 파일 + WSL2) |
| WSL2 (Ubuntu) | 빌드/배포 실행 환경 | 권장 | `wsl --install` |
| psql / redis-cli | DB/캐시 점검 | ❌ | (Docker 컨테이너로 대체 가능) |
| curl / Postman | API 테스트 | ❌ | `Postman.Postman` |
| AWS CLI | file-s3 모듈 S3 테스트 | ❌ | `Amazon.AWSCLI` |

> winget 이 없으면 각 공식 사이트 설치 파일로 받아도 된다. choco 사용자는 대응 패키지로.

---

## 1. JDK 21 (Temurin)

```powershell
winget install -e --id EclipseAdoptium.Temurin.21.JDK
```
설치 후 새 터미널에서 확인:
```powershell
java -version      # openjdk version "21..." 확인
```
`JAVA_HOME` 이 JDK 21 을 가리키는지 확인(여러 JDK 가 있으면 명시). IntelliJ 는 자체 SDK 설정을 쓰므로 시스템 JAVA_HOME 과 별개로 프로젝트 SDK 도 21 로 맞춘다(아래 4번).

> 베이스 이미지가 `eclipse-temurin:21-jre` 라 Temurin 21 로 맞추면 로컬·컨테이너 런타임이 일치한다.

---

## 2. Git

```powershell
winget install -e --id Git.Git
git clone https://github.com/frusianblue/si-msa-framework.git
```
줄바꿈 이슈 예방(이 프로젝트는 Spotless 가 `lineEndings=UNIX` 고정): `git config --global core.autocrlf input`.

---

## 3. Gradle — 설치 불필요 (래퍼 동봉)

프로젝트에 Gradle 8.14 래퍼가 들어 있다. **Gradle 을 따로 깔지 말고** 래퍼를 쓴다.
- Windows 네이티브: `gradlew.bat <task>`
- WSL2/Git Bash: `./gradlew <task>`

---

## 4. IntelliJ IDEA

```powershell
winget install -e --id JetBrains.IntelliJIDEA.Community
```
Community 로 충분하다(Spring 전용 보조 기능이 필요하면 Ultimate). 프로젝트 열고 나서:

1. **Project SDK = JDK 21**: File → Project Structure → Project → SDK = 21, Language level = 21.
2. **Gradle JVM = JDK 21**: Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM = 21.
3. **Lombok 어노테이션 처리 켜기**(필수): Settings → Build → Compiler → Annotation Processors → **Enable annotation processing** 체크. (Lombok 플러그인은 최근 IntelliJ 에 기본 번들. 없으면 마켓플레이스에서 "Lombok" 설치.)
   - 이 프로젝트는 Lombok + MapStruct(1.6.3) + `lombok-mapstruct-binding` 을 함께 쓴다. 어노테이션 처리를 켜야 `@Mapper`/`@Builder` 등이 IDE 에서 정상 인식된다.
4. **코드 포맷 = Spotless(Palantir)**: 포맷은 IDE 포매터 대신 `./gradlew spotlessApply` 로 맞춘다(CI 는 `spotlessCheck` 게이트). IDE 에 Palantir Java Format 플러그인을 깔아도 되지만, 단일 소스는 Gradle 태스크다.

---

## 5. Docker Desktop (+ WSL2)

이미지 빌드, 로컬 Postgres/Redis 컨테이너, kind 모두 Docker 가 전제다. 설치/메모리/WSL2 토글은
`docs/modules/LOCAL_K8S_ENV_SETUP.md` B절(Windows)과 동일하니 그쪽을 따른다. 요지:
- `wsl --install` → Docker Desktop 설치 → Settings 에서 **WSL 2 based engine** + **WSL Integration(Ubuntu)** ON → 메모리 6GB+.
- 이후 WSL2 Ubuntu 셸에서 `docker`/`kubectl`/`kind` 가 동작.

---

## 6. 로컬 DB / Redis (프로파일별)

- **local 프로파일** = H2 인-메모리(인-잼). **아무것도 설치 안 해도** `bootRun` 으로 뜬다 → 첫 기동 테스트는 이걸로.
- **postgres 가 필요한 경우**(local-postgres / prod 프로파일 흉내) — Docker 로 띄운다:
```bash
docker run -d --name pg -p 5432:5432 \
  -e POSTGRES_USER=user_app -e POSTGRES_PASSWORD=dev-userpass -e POSTGRES_DB=userdb \
  postgres:16-alpine
docker run -d --name redis -p 6379:6379 redis:7-alpine
```
auth-server 는 `authdb` 가 추가로 필요(`docker exec pg psql -U user_app -c "CREATE DATABASE authdb;"`). DB 점검은 `psql`/`redis-cli` 또는 `docker exec` 로.

---

## 7. 프로젝트 빌드 / 실행

WSL2(권장) 또는 Git Bash 기준(`./gradlew`), 네이티브면 `gradlew.bat`:

```bash
# 전체 빌드(테스트 포함)
./gradlew build

# 포맷 정렬(최초 1회 + 수정 후)
./gradlew spotlessApply

# 단일 서비스 로컬 기동 (H2, 설치 0)
./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local'

# 부트 JAR 만들기
./gradlew :services:user-service:bootJar      # services/user-service/build/libs/user-service-1.0.0.jar
```

서비스별 포트(local): gateway 8000 · auth-server 9000 · user-service 8080 · admin-service 8081.
각 서비스 `README.md` 에 프로파일/엔드포인트/`bootRun` 예시가 있다.

API 문서(swagger-ui): springdoc 3.0.3 → 기동 후 `/swagger-ui.html`.

---

## 8. 검증 체크리스트
```bash
java -version            # 21
git --version
./gradlew --version      # Gradle 8.14
docker run --rm hello-world
./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local'  # 헬스 200 확인 후 종료
```
`/actuator/health` 가 200 이면 로컬 개발 환경 준비 완료. 다음은 k8s 로 올리기 →
`docs/modules/LOCAL_K8S_ENV_SETUP.md`(도구) → `docs/modules/K8S_ADDONS.md`(애드온) → `docs/modules/LOCAL_K8S_TEST.md`(배포).
