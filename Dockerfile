FROM eclipse-temurin:21-jdk-alpine AS builder
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
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
