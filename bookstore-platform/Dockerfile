# syntax=docker/dockerfile:1
# One multi-stage build, parameterized by SERVICE (the Maven module to build+run).
# docker-compose passes SERVICE per service, so each gets its own image from the
# same JDK-build -> JRE-run pipeline.

FROM maven:3.9-eclipse-temurin-17 AS build
ARG SERVICE
WORKDIR /workspace
COPY . .
# -am also builds the module's dependencies (the common library).
RUN mvn -q -pl ${SERVICE} -am -DskipTests package

FROM eclipse-temurin:17-jre AS runtime
ARG SERVICE
WORKDIR /app
COPY --from=build /workspace/${SERVICE}/target/${SERVICE}-*.jar /app/app.jar
# Run as a non-root user.
RUN useradd -r -u 1001 appuser
USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
