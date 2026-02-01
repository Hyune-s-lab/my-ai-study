### Step 1: Build
FROM gradle:9.2.1-jdk25 AS build

ARG MODULE_PATH=opengateway-mcp

WORKDIR /app

COPY . .

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :opengateway-mcp:bootJar --no-daemon -x test

### Step 2: Runtime
FROM eclipse-temurin:25-jre-jammy

ARG MODULE_PATH=opengateway-mcp

WORKDIR /app

COPY --from=build /app/${MODULE_PATH}/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
