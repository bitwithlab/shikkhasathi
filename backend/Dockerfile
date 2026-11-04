# ShikkhaSathi backend — Spring Boot (Java 21). Build context: backend/
# One image, two roles: SS_ROLE=api (HTTP only) or SS_ROLE=worker (job workers + schedules), default all.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/shikkhasathi-backend-*.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
# fontconfig + DejaVu (Java2D text for invoice PDFs) and wget (health check) ship with the base image.
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app.jar app.jar
RUN mkdir -p /app/storage-local && chown app:app /app/storage-local
USER app
ENV PORT=8080 JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
