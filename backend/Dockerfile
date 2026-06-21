FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace

COPY settings.gradle build.gradle ./
COPY src ./src
RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

ENV JAVA_OPTS="-Xms128m -Xmx512m"

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
