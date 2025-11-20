# STAGE 1: Build
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Copy only pom.xml first to cache dependencies (Layer caching optimization)
COPY pom.xml .

RUN mvn dependency:go-offline

# Copy source and build
COPY src ./src

RUN mvn clean package -DskipTests

# STAGE 2: Run (The actual 12-factor image)
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

USER spring:spring

# Copy JAR from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Factor III: Expose port (documentation only, actually controlled by env)
EXPOSE 8080

# Factor XI: Stream logs to stdout (default in Spring Boot)
ENTRYPOINT ["java", "-jar", "app.jar"]

