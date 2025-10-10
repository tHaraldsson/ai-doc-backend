FROM gradle:7.6-jdk21 AS build
WORKDIR /app
COPY build.gradle .
COPY src ./src
RUN gradle clean build -x test

FROM openjdk:21-jdk-slim
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]