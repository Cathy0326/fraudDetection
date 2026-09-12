# ---------- Stage 1: build the React bundle ----------
FROM node:22-alpine AS frontend
WORKDIR /frontend

# Copy manifests first so `npm ci` is cached until dependencies actually change.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
# --outDir overrides vite.config.ts, whose relative path points outside this stage.
RUN npm run build -- --outDir dist --emptyOutDir


# ---------- Stage 2: build the Spring Boot jar ----------
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build

COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
# The frontend bundle is not in the repo (.gitignore), so it is injected here.
COPY --from=frontend /frontend/dist ./src/main/resources/static

# Tests are skipped: Testcontainers needs a Docker daemon this stage cannot reach.
RUN mvn -B clean package -DskipTests


# ---------- Stage 3: runtime ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

# Wildcard avoids hardcoding the version; *.jar does not match the .jar.original left by repackage.
COPY --from=backend /build/target/*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
