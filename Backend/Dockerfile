# Build stage: compile and bundle all dependencies into one runnable jar
FROM gradle:8.10-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle shadowJar --no-daemon

# Run stage: just the JRE + the built jar, nothing else
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
CMD ["java", "-jar", "app.jar"]