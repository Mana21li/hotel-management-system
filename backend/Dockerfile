# =============================================================================
# Multi-stage build for the public API strangler/gateway (:8080).
#
# Stage 1 (build): full JDK + Gradle wrapper → produces the Spring Boot fat jar.
# Stage 2 (runtime): slim JRE only → smaller image, smaller attack surface.
#
# Build + run (from repo root):
#     docker compose --profile app up -d --build
# =============================================================================

# ---- Stage 1: build ---------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy the Gradle wrapper and build files first, so Docker caches the
# dependency-download layer. Source changes then reuse this layer.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon > /dev/null 2>&1 || true

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# ---- Stage 2: runtime -------------------------------------------------------
FROM eclipse-temurin:25-jre
WORKDIR /app

# Run as a non-root user (production hygiene).
RUN useradd --system --create-home appuser
USER appuser

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
