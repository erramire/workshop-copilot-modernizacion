# Etapa de build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

# Etapa de runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 1001 app && adduser -S -u 1001 -G app app
WORKDIR /app

COPY --from=build /workspace/target/student-web-app.jar app.jar
COPY --from=build /workspace/target/agent/applicationinsights-agent.jar applicationinsights-agent.jar

USER 1001
EXPOSE 8080

# El agente de Application Insights solo se adjunta si existe su connection string (ADR-010)
ENTRYPOINT ["sh", "-c", "exec java ${APPLICATIONINSIGHTS_CONNECTION_STRING:+-javaagent:/app/applicationinsights-agent.jar} -jar /app/app.jar"]
