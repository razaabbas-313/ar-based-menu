# =========================================================================
# Monorepo Root Dockerfile (works when Root Directory is not set on Render)
# =========================================================================

FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY backend/pom.xml .
RUN mvn dependency:go-offline -B || true

COPY backend/src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd -r atelier && useradd -r -g atelier -m -d /app atelier

COPY --from=builder /build/target/atelier-media-engine-*.jar /app/app.jar

RUN chown -R atelier:atelier /app

USER atelier

ENV PORT=8080
ENV JAVA_OPTS="-Xms192m -Xmx320m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=128m -Djava.awt.headless=true -Dfile.encoding=UTF-8"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${PORT}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT} -jar /app/app.jar"]
