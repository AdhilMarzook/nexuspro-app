# Multi-stage build — produces minimal production image
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy pom and download deps first (layer cache)
COPY pom.xml .
COPY src ./src

RUN apk add --no-cache maven && mvn package -DskipTests -q

# Stage 2: Runtime — minimal JRE only
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: run as non-root
RUN addgroup -S nexuspro && adduser -S nexuspro -G nexuspro

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

# JVM security hardening
RUN chown nexuspro:nexuspro app.jar

USER nexuspro

# JVM tuning: G1GC, container-aware heap, no debug ports
ENTRYPOINT ["java", \
  "-XX:+UseG1GC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseContainerSupport", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:docker}", \
  "-jar", "app.jar"]

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
