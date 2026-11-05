FROM eclipse-temurin:25-jdk AS build

ARG SERVICE_DIR
WORKDIR /workspace

COPY common ./common
COPY ${SERVICE_DIR} ./${SERVICE_DIR}

WORKDIR /workspace/${SERVICE_DIR}
RUN chmod +x gradlew && \
    ./gradlew bootJar --no-daemon && \
    find build/libs -maxdepth 1 -type f ! -name '*-plain.jar' -exec cp {} /tmp/app.jar \;

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app
RUN apk add --no-cache curl

COPY --from=build /tmp/app.jar ./app.jar

USER 10001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
