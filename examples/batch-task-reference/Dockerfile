# 멀티스테이지: 빌드(JDK 21) → 런타임(JRE 21). 태스크는 한 번 실행 후 종료하므로 슬림 런타임이면 충분.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar /app/app.jar
# 컨테이너 종료코드 = JVM 종료코드(배치 실패 시 ≠0). entrypoint 가 exec 형태여야 신호/코드가 그대로 전달된다.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
