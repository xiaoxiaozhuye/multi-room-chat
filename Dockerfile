FROM node:20-alpine AS frontend-builder
WORKDIR /build/frontend

COPY frontend/package*.json ./
RUN npm ci

COPY frontend ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-17 AS backend-builder
WORKDIR /build

COPY pom.xml ./
COPY src ./src
COPY sql ./sql

RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre-jammy
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends nginx \
    && rm -rf /var/lib/apt/lists/* \
    && rm -f /etc/nginx/sites-enabled/default

WORKDIR /app

COPY --from=backend-builder /build/target/*.jar /app/app.jar
COPY --from=backend-builder /build/sql/migrations /app/sql/migrations
COPY --from=frontend-builder /build/frontend/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN sed -i 's/\r$//' /app/docker-entrypoint.sh

EXPOSE 80

ENTRYPOINT ["/usr/bin/env", "bash", "/app/docker-entrypoint.sh"]
