# Bookstore Platform — Capstone Project

A Spring Boot online bookstore that evolves, step by step, from a single monolith into a
containerized microservices platform on AWS.

**This repository is built in phases, and the commit history is meant to be read as the story of
that evolution.** Each phase is tagged (`phase-1`, `phase-2`, …).

## Tech Stack

| Area | Choice |
|---|---|
| Language / Build | Java 17, Maven, Spring Boot 3.x |
| Database | PostgreSQL (Docker), Flyway migrations |
| Testing | JUnit 5, Mockito, Testcontainers |
| Service-to-service | OpenFeign + Resilience4j |
| Config | Spring Cloud Config |
| Messaging | Apache Kafka |
| Edge | Spring Cloud Gateway |
| Cloud | AWS S3, Lambda, DynamoDB, SNS/SES |
| Ops | Docker, Kubernetes, GitHub Actions, Actuator |

## Architecture (target)

```
                   [ Web frontend (optional) ]
                              |
                       [ API Gateway ]              routing + edge JWT auth
                              |
   ┌──────────────┬───────────┴───────────┬──────────────────┐
 user-service   book-service         order-service     payment-service
    (PG)           (PG)                  (PG)               (PG)
                     ^                    |
                     |  Feign + Resilience4j    publishes OrderPlaced / PaymentCompleted
                     |                    v
                                      [ Kafka ]
                                      /        \
                       notification-service   analytics-service

  config-server (Spring Cloud Config) serves configuration to all services

  Cover upload:  S3 --event--> Lambda --> DynamoDB (CoverMetadata) --> SNS/SES (email)
  DynamoDB also stores per-user browsing history (recently-viewed books, TTL 30d)
```

## Documentation

| File | What it is |
|---|---|
| [`CLAUDE.md`](CLAUDE.md) | Development rules and hard boundaries for this repo |
| [`docs/00-project-overview.md`](docs/00-project-overview.md) | Annotated requirement walkthrough |
| [`docs/01-ROADMAP.md`](docs/01-ROADMAP.md) | Phase-by-phase plan with a Definition of Done for each |
| [`docs/PROGRESS.md`](docs/PROGRESS.md) | Running progress log |
| [`docs/BACKLOG.md`](docs/BACKLOG.md) | Deferred items and stretch goals |

## Status

Phase 10 complete: eight services under [`bookstore-platform/`](bookstore-platform/) (user, book,
order, payment, config-server, notification, analytics, api-gateway) + a shared `common` library and a
`cover-image-lambda`. Event-driven via Kafka, fronted by a Spring Cloud Gateway, config from Spring
Cloud Config, AWS (S3/Lambda/DynamoDB/SNS) for covers + browsing history, and now fully containerized:
a one-command `docker compose` stack and `k8s/` manifests. The Phase 1–4 monolith lives in history
under the `phase-1`…`phase-4` tags. See `docs/PROGRESS.md` and `docs/02-DESIGN.md`.

## Local Development

Prerequisites: JDK 17, Maven, Docker.

### Whole stack in one command (Phase 10)

```bash
cd bookstore-platform
docker compose up --build            # PostgreSQL + Kafka + all 8 services
curl localhost:8080/actuator/health  # everything is reached via the gateway (:8080)
```

The first build is slow (each image runs Maven). The stack wants ~6 GB — on Colima,
`colima start --memory 6`. Kubernetes manifests + guide are in [`bookstore-platform/k8s/`](bookstore-platform/k8s/).

### Or run modules directly (for fast iteration)

```bash
# 1. Just the infra (PostgreSQL with a database per service, + Kafka)
cd bookstore-platform
docker compose up -d postgres kafka

# 2. Build and test the whole platform (all modules)
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn clean verify

# 3. Run services (each in its own terminal); host talks to Kafka on localhost:29092
cd config-server && mvn spring-boot:run   # :8888
cd user-service  && mvn spring-boot:run   # :8081
cd book-service  && mvn spring-boot:run   # :8082
# ... order :8083, payment :8084, notification :8085, analytics :8086, api-gateway :8080

curl localhost:8081/actuator/health   # -> {"status":"UP"}
```

> A single API entry point (gateway) and running the services in Docker Compose come in Phases 8 and 10;
> for now each service is reached on its own port.

### Running the tests (Testcontainers)

The integration tests use [Testcontainers](https://testcontainers.com/), which needs a Docker engine
that `docker-java` can talk to. On this project's dev machine, **Docker Desktop 4.83's API-proxy is
incompatible with docker-java**, so tests run against **Colima**:

```bash
colima start                     # once per session; provides a plain dockerd
```

Then `mvn clean verify` works because:
- `~/.testcontainers.properties` sets `docker.host` to Colima's socket (machine-local, not committed);
- the Surefire config pins `api.version=1.41` (committed) — docker-java defaults to Docker API 1.32,
  which modern engines (min 1.40) reject.

Colima also can't bind-mount its docker socket into Testcontainers' Ryuk reaper, so Ryuk is disabled
in the build itself (`TESTCONTAINERS_RYUK_DISABLED=true` in the Surefire config; the JVM shutdown hook
cleans up containers instead) — no env var needed.

On a standard Docker setup (e.g. CI / Linux), none of the above is needed — plain `mvn clean verify`
works. Full diagnosis in [`docs/BUGLOG.md`](docs/BUGLOG.md).

## Security Note

This is a public repository. No credentials, tokens, or keys are committed. AWS access is resolved
through the SDK's default credential chain (`~/.aws/credentials` or environment variables); JWT and
database secrets are supplied via environment variables.
