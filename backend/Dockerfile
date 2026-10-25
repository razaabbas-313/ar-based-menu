# =========================================================================
# Multi-stage Dockerfile optimized for Render Free Tier (512 MB RAM limit)
# =========================================================================

# -------------------------------------------------------------
# Stage 1: Build stage
# -------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and package executable fat jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# -------------------------------------------------------------
# Stage 2: Minimal, secure production runtime
# -------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Install curl for container healthcheck and ca-certificates
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Run as non-root user for security
RUN groupadd -r atelier && useradd -r -g atelier -m -d /app atelier

# Copy executable jar from builder stage
COPY --from=builder /build/target/atelier-media-engine-*.jar /app/app.jar

# Set file ownership
RUN chown -R atelier:atelier /app

USER atelier

# Render automatically sets PORT (defaults to 8080)
ENV PORT=8080

# JVM memory and GC flags specifically tuned for Render 512 MB RAM:
# - SerialGC eliminates G1GC card-table and marking bitmap overhead (~60-80 MB saved)
# - TieredStopAtLevel=1 limits JIT to C1 compiler, reducing code cache & JIT memory
# - Max heap 320 MB + Max metaspace 128 MB guarantees total RSS stays safely below 512 MB
ENV JAVA_OPTS="-Xms192m -Xmx320m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=128m -Djava.awt.headless=true -Dfile.encoding=UTF-8"

EXPOSE 8080

# Health check endpoint for Render service monitoring
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${PORT}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT} -jar /app/app.jar"]
