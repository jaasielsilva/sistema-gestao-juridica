# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package

FROM eclipse-temurin:25-jre
# Aplica correcoes de seguranca do sistema publicadas depois da imagem base.
# Tambem remove o binario "pebble" da imagem base: esta aplicacao nao o usa (menos superficie de ataque).
RUN apt-get update && apt-get -y upgrade && rm -rf /var/lib/apt/lists/* && rm -f /usr/bin/pebble
RUN useradd --system --uid 10001 --no-create-home appuser
WORKDIR /app
COPY --from=build /build/target/app.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
