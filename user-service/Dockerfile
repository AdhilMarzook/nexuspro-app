# Uses the pre-built JAR from mvn package — no Maven needed inside Docker
FROM eclipse-temurin:21-jre-alpine

# Security: run as non-root
RUN addgroup -S nexuspro && adduser -S nexuspro -G nexuspro

WORKDIR /app

# Copy the built JAR from the target folder
COPY target/*.jar app.jar

RUN chown nexuspro:nexuspro app.jar

USER nexuspro

ENTRYPOINT ["java", \
  "-XX:+UseG1GC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseContainerSupport", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
