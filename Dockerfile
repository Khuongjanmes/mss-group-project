# Dockerfile dùng chung. Build từ thư mục gốc, ví dụ:
#   docker build --build-arg SERVICE=identity-service -t openevent/identity-service .
FROM maven:3.9-eclipse-temurin-21-alpine AS build
ARG SERVICE
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl "${SERVICE}" -am -DskipTests package \
    && cp "${SERVICE}"/target/*.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=build /app.jar /app/app.jar
ENV JAVA_TOOL_OPTIONS="-Xmx256m -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
