FROM gradle:8.10-jdk21 AS build
WORKDIR /app
COPY build.gradle .
COPY src ./src
RUN gradle clean build -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]