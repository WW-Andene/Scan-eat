# ============================================================
# Stage 1: Build
# ============================================================
FROM gradle:8.9-jdk17 AS builder

WORKDIR /app
COPY settings.gradle.kts build.gradle.kts ./

# Download deps first (cacheable layer) - src isn't copied in yet, so this
# layer only invalidates on dependency changes, not on every source edit.
RUN gradle dependencies --no-daemon --quiet 2>/dev/null || true

COPY src ./src

# Build fat jar
RUN gradle shadowJar --no-daemon --info

# ============================================================
# Stage 2: Runtime — minimal JRE image
# ============================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Non-root user
RUN addgroup -S scanneat && adduser -S scanneat -G scanneat
USER scanneat

COPY --from=builder /app/build/libs/scan-eat-server.jar ./server.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-Xmx256m", "-jar", "server.jar"]
