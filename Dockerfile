# ==============================================================================
# Multi-Stage Dockerfile for SeekFactory Spring Boot Backend (Java 25)
# ==============================================================================

# ─── Stage 1: Build Application with Maven and Java 25 ───
FROM maven:3.9.9-eclipse-temurin-21-alpine AS maven_base

# Use JDK 25 image for compilation and packaging
FROM eclipse-temurin:25-jdk-alpine AS builder

# Install maven
RUN apk add --no-cache maven

WORKDIR /build

# 1. Copy POM and source code
COPY pom.xml .
COPY src ./src

# 2. Build executable JAR with Java 25
RUN mvn clean package -DskipTests

# ─── Stage 2: Minimal Java 25 Runtime ───
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Non-root user for container security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy compiled JAR from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Set permissions
RUN chown -R appuser:appgroup /app
USER appuser

# Expose port (Render overrides with $PORT dynamically)
ENV PORT=8080
EXPOSE 8080

# Run Spring Boot Application on Java 25 with preview features
ENTRYPOINT ["sh", "-c", "java --enable-preview -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dserver.port=${PORT} -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
