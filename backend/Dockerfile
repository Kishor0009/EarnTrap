# =========================================================================
# Stage 1: Build stage (compile source and package application JAR)
# =========================================================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml first to take advantage of Docker layer caching for dependencies
COPY pom.xml .

# Download dependencies (skip if pom.xml has not changed)
RUN mvn dependency:go-offline -B || true

# Copy source files
COPY src ./src

# Build production executable JAR without running tests during image build
RUN mvn clean package -DskipTests

# =========================================================================
# Stage 2: Runtime stage (minimal, secure, container-optimized Java 17 JRE)
# =========================================================================
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Create a dedicated non-privileged system user for security
RUN groupadd --system earntrap && useradd --system --gid earntrap earntrap

# Copy packaged JAR from the build stage
COPY --from=builder /app/target/*.jar app.jar

# Set file ownership to non-root user
RUN chown earntrap:earntrap app.jar

# Switch to non-root user
USER earntrap

# Default port (Render injects PORT dynamically at runtime)
ENV PORT=8080
EXPOSE 8080

# Run Spring Boot with container-aware JVM flags and dynamic PORT mapping
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dserver.port=${PORT} -jar app.jar"]
