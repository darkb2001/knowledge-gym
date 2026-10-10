# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-alpine AS builder
# Agent is inert unless the explicit observability overlay adds -javaagent.
ADD --checksum=sha256:f787eb6c7f3d18e69a431e108a15278d25ee37f83d68b678f621e063f3988f82 https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.32.0/opentelemetry-javaagent.jar /otel-javaagent.jar
WORKDIR /app
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY kg-core kg-core
COPY kg-infrastructure kg-infrastructure
COPY kg-presentation kg-presentation
COPY kg-agent kg-agent
RUN chmod +x gradlew && ./gradlew :kg-presentation:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=builder /app/kg-presentation/build/libs/*.jar app.jar
COPY --from=builder /otel-javaagent.jar /app/otel-javaagent.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
