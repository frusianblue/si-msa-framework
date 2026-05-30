# deploy/docker/Dockerfile
#
# 전제: 빌드(jar)는 Jenkins 에서 ./gradlew :services:user-service:bootJar 로 먼저 수행.
#       Docker 는 그 산출물만 받아 런타임 이미지로 만든다(컨테이너 안에서 재빌드 X).
#
# Jenkins 예:
#   ./gradlew :services:user-service:bootJar --no-daemon
#   docker build -f deploy/docker/Dockerfile \
#     --build-arg JAR_FILE=services/user-service/build/libs/user-service-1.0.0.jar \
#     -t registry.example.com/si-msa/user-service:$GIT_SHA .
#
# 팁: 실행형 서비스 모듈은 build.gradle 에 `jar { enabled = false }` 를 넣어
#     -plain.jar 가 안 생기게 하면 JAR_FILE 경로가 항상 하나로 깔끔해진다.

# ===== layer 추출 stage (캐시 최적화) =====
FROM eclipse-temurin:21-jre AS builder
WORKDIR /builder
ARG JAR_FILE
COPY ${JAR_FILE} application.jar
# Spring Boot 3.2+/4 의 레이어 추출 (jarmode=tools)
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ===== runtime stage =====
FROM eclipse-temurin:21-jre
WORKDIR /application
# 비루트 실행
RUN useradd -r -u 1001 appuser
# 변경 빈도 낮은 레이어부터 복사 → 도커 레이어 캐시 적중률 ↑
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./
USER appuser
EXPOSE 8080
# 컨테이너 메모리 인지 + 가상스레드는 application.yml 에서 활성화
# Boot 3.2+/4 의 런처 메인 클래스 사용(과거 `java -jar app.jar` 대체)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
