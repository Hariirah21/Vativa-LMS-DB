# --------------------------------------------------------------------------
# Stage 1: Build
# Uses a JDK 21 image with Maven (matches <java.version>21</java.version>
# in your pom.xml) to compile and package the app into a jar.
# --------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy only the pom first so Docker can cache the dependency download layer
# separately from your source code - source changes won't force a full
# dependency re-download on every build.
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Now copy the rest of the source and build.
COPY src ./src
RUN mvn -B clean package -DskipTests

# --------------------------------------------------------------------------
# Stage 2: Runtime
# Slim JRE-only image (no Maven, no JDK compiler) - smaller and lower attack
# surface than shipping the build stage itself.
# --------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Run as a non-root user (OWASP container hardening - never run the JVM as
# root inside the container).
RUN useradd --system --uid 1001 appuser
USER appuser

COPY --from=build /app/target/*.jar app.jar

# Render sets the PORT env var at runtime and routes traffic to it - make
# sure application.yml/properties has: server.port=${PORT:8080}
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]