# 멀티스테이지: 빌드(JDK 21) → 런타임(JRE 21). 잡은 한 번 실행 후 종료(System.exit)하므로 슬림 런타임이면 충분.
#   ⚠️ 빌드 전제: 로컬 mavenLocal 에 com.company:framework-task:1.0.0 이 publish 되어 있어야 한다
#   (repo 루트에서 ./gradlew :framework:framework-task:publishToMavenLocal).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar /app/app.jar
# 컨테이너 종료코드 = JVM 종료코드(배치 실패 시 ≠0). exec 형태여야 신호/코드가 그대로 전달된다.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
