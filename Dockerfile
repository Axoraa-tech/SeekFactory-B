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

# ffmpeg compresses uploaded seek videos (H.264/AAC); without it videos are stored as uploaded
RUN apk add --no-cache ffmpeg

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
# Memory: a 512 MB instance also has to fit metaspace, threads and an ffmpeg process, so the heap
# gets 50% instead of 75% (75% got the container OOM-killed mid-upload -> 503). SerialGC and C1-only
# JIT cut JVM overhead and start-up time on the single small CPU.
ENTRYPOINT ["sh", "-c", "java --enable-preview -XX:+UseContainerSupport -XX:MaxRAMPercentage=50.0 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -Dserver.port=${PORT} -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
