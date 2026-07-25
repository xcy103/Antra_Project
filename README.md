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

Phase 0 complete: Spring Boot monolith skeleton (`bookstore/`) boots and reports healthy; a
single-PostgreSQL `docker-compose.yml` is in place. No business code yet.

## Local Development

Prerequisites: JDK 17, Maven, Docker.

```bash
# 1. Local PostgreSQL (used from Phase 2 onward; Phase 0 app does not connect to it yet)
cp .env.example .env          # then edit credentials if you like
docker compose up -d postgres

# 2. Build and test the monolith
cd bookstore
mvn clean verify

# 3. Run it
mvn spring-boot:run
curl localhost:8080/actuator/health   # -> {"status":"UP"}
```

> Note: `docker compose up -d postgres` maps host port **5432**. If a local PostgreSQL is already
> running there, stop it first or remap the host port in `docker-compose.yml`.

## Security Note

This is a public repository. No credentials, tokens, or keys are committed. AWS access is resolved
through the SDK's default credential chain (`~/.aws/credentials` or environment variables); JWT and
database secrets are supplied via environment variables.
