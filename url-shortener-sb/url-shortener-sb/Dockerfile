FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy wrapper and POM first to maximize dependency cache reuse.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Ensure wrapper script is executable and line endings are Linux-safe.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Pre-fetch dependencies as a dedicated cached layer.
RUN ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp clean package -DskipTests && cp target/*SNAPSHOT.jar app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd --create-home --shell /usr/sbin/nologin appuser
USER appuser

COPY --from=build /app/app.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

