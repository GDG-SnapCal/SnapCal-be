# ---- Build Stage ----
FROM gradle:8.7-jdk17 AS builder
WORKDIR /app

# 의존성 캐시 레이어 분리 (소스 변경 시 재다운로드 방지)
COPY build.gradle settings.gradle* ./
RUN GRADLE_OPTS="-Xmx384m -Xms128m -XX:+UseSerialGC" gradle dependencies --no-daemon || true

COPY src ./src
RUN GRADLE_OPTS="-Xmx384m -Xms128m -XX:+UseSerialGC" gradle bootJar --no-daemon -x test --max-workers=1

# ---- Run Stage ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# 보안: root 아닌 전용 유저로 실행
RUN groupadd --system snapcal && useradd --system --gid snapcal snapcal
USER snapcal

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", \
  "-Dspring.profiles.active=${SPRING_PROFILE:-local}", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "app.jar"]
