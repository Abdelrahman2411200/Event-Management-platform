FROM maven:3.9.11-eclipse-temurin-17-alpine AS build

ARG MODULE
WORKDIR /workspace

COPY pom.xml ./
COPY shared ./shared
COPY api-gateway ./api-gateway
COPY auth-service ./auth-service
COPY event-service ./event-service
COPY venue-service ./venue-service
COPY attendee-service ./attendee-service
COPY payment-service ./payment-service
COPY notification-service ./notification-service

RUN mvn -B -pl "${MODULE}" -am clean package -DskipTests \
    && cp "/workspace/${MODULE}/target/"*.jar /workspace/application.jar

FROM eclipse-temurin:17-jre-alpine

ARG MODULE
ENV APP_MODULE=${MODULE} \
    JAVA_OPTS="" \
    SERVER_PORT=8080

RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/application.jar application.jar

USER spring
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=35s --retries=10 \
  CMD wget --quiet --spider "http://localhost:${SERVER_PORT}/actuator/health/readiness" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/application.jar"]
