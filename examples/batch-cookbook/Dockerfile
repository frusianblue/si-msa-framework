# 멀티스테이지: 빌드(JDK 21) → 런타임(JRE 21). run-once 태스크라 슬림 런타임이면 충분.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar /app/app.jar
# 종료코드 전파를 위해 exec 형태(쉘 래핑 금지). 실행할 Job 은 args 로 주입(아래 k8s 참조).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
