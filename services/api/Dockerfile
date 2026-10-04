# Build context is the monorepo root (see infra/docker-compose.yml):
#   docker build -f services/api/Dockerfile -t aakar-api .
FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace
COPY services/api/settings.gradle.kts services/api/build.gradle.kts ./
RUN gradle dependencies --no-daemon -q > /dev/null 2>&1 || true
COPY services/api/src ./src
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 aakar
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
# The Buti motif library behind GET /api/motifs and the motif checks on designs, at the same path the geometry image
# uses; aakar.profile=production refuses to start without it.
COPY packages/design-tokens/motifs /design-tokens/motifs
ENV AAKAR_MOTIFS_DIR=/design-tokens/motifs
USER aakar
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s CMD ["sh", "-c", "wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '\"UP\"'"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
