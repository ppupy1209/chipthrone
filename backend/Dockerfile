FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace

COPY settings.gradle build.gradle ./
COPY src ./src
RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

ENV JAVA_OPTS="-Xms128m -Xmx512m"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/readiness > /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
