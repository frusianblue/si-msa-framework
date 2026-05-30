# ===== build stage =====
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
COPY . .
# 빌드할 서비스명을 build-arg 로 전달 (예: services:user-service)
ARG SERVICE=services:user-service
RUN ./gradlew :${SERVICE}:bootJar -x test --no-daemon
RUN cp $(find . -path "*build/libs/*.jar" | grep -v plain | head -1) app.jar
# 레이어 추출 (변경 적은 레이어를 캐시)
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ===== runtime stage =====
FROM eclipse-temurin:21-jre
WORKDIR /app
# 비루트 사용자
RUN useradd -r -u 1001 appuser
COPY --from=builder /workspace/extracted/dependencies/ ./
COPY --from=builder /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/extracted/application/ ./
USER appuser
EXPOSE 8080
# 컨테이너 메모리 인지 + 가상스레드는 yml에서 활성
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
