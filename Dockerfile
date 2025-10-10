FROM openjdk:21-jdk AS build
WORKDIR /app

COPY build.gradle .
COPY gradlew .
COPY gradlew.bat .
COPY gradle/wrapper/ gradle/wrapper/
COPY src ./src

RUN chmod +x gradlew
RUN ./gradlew clean build -x test

FROM openjdk:21-jdk-slim
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]