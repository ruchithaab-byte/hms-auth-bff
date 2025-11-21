# STAGE 1: Build
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy pom.xml first
COPY pom.xml .

# Copy local dependencies (common lib JAR)
# The prepare-build-context.sh script ensures libs/ directory exists
COPY libs/ /tmp/libs/

# Install local dependencies (JAR, POM, and parent POM) and resolve all dependencies
# This ensures the common lib and its parent are available before dependency resolution
# BuildKit cache mount speeds up subsequent builds by caching Maven repository
RUN --mount=type=cache,target=/root/.m2 \
    if [ -f /tmp/libs/hms-common-lib-1.0.0-SNAPSHOT.jar ]; then \
        echo "Installing parent POM..." && \
        mvn install:install-file \
            -Dfile=/tmp/libs/hms-platform-libraries-1.0.0-SNAPSHOT.pom \
            -DgroupId=com.hms.platform \
            -DartifactId=hms-platform-libraries \
            -Dversion=1.0.0-SNAPSHOT \
            -Dpackaging=pom && \
        echo "Installing common library..." && \
        mvn install:install-file \
            -Dfile=/tmp/libs/hms-common-lib-1.0.0-SNAPSHOT.jar \
            -DpomFile=/tmp/libs/hms-common-lib-1.0.0-SNAPSHOT.pom \
            -DgroupId=com.hms.platform \
            -DartifactId=hms-common-lib \
            -Dversion=1.0.0-SNAPSHOT \
            -Dpackaging=jar; \
    fi && \
    mvn dependency:go-offline

# Copy API contracts (for OpenAPI code generation)
# The build-local.sh script ensures api-contracts/ directory exists
# Using a pattern that won't fail if directory is empty (Docker handles this gracefully)
COPY api-contracts/ /build/api-contracts/

# Copy source and build
COPY src ./src

# Build with cache mount for faster builds
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests

# STAGE 2: Run (The actual 12-factor image)
FROM --platform=$TARGETPLATFORM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Create a non-root user for security
RUN addgroup --system spring && adduser --system --ingroup spring spring

USER spring:spring

# Copy JAR from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Factor III: Expose port (documentation only, actually controlled by env)
EXPOSE 8080

# Factor XI: Stream logs to stdout (default in Spring Boot)
ENTRYPOINT ["java", "-jar", "app.jar"]

