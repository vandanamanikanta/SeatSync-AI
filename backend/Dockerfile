# ==============================================================================
# SeatSync AI - Backend Multi-Stage Dockerfile
# Stage 1: Build with Maven 3.9 & Eclipse Temurin JDK 21 Alpine
# Stage 2: Lightweight Eclipse Temurin JRE 21 Alpine Runtime
# ==============================================================================

# --- Stage 1: Build Stage ---
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy project descriptor and source code
COPY pom.xml .
COPY src ./src

# Compile and package production fat JAR without re-running unit tests during image build
RUN mvn --batch-mode clean package -DskipTests

# --- Stage 2: Runtime Stage ---
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# Install curl and wget for container health checks
RUN apk add --no-cache wget curl

# Create non-root system user and group for security hardening
RUN addgroup -S seatsync && adduser -S seatsync -G seatsync

# Copy compiled jar from build stage
COPY --from=builder /build/target/seatsync-backend-1.0.0-SNAPSHOT.jar app.jar

# Set ownership to unprivileged user
RUN chown -R seatsync:seatsync /app

# Switch to non-root execution
USER seatsync

EXPOSE 8081

ENV PORT=8081
ENV SPRING_PROFILES_ACTIVE=prod

HEALTHCHECK --interval=20s --timeout=5s --start-period=45s --retries=3 \
  CMD wget -qO- http://localhost:8081/actuator/health | grep -q 'UP' || exit 1

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

