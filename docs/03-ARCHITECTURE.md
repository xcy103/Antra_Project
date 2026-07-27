# Architecture — Full Picture

The one-page view of the whole system: client, edge, services, data, messaging, cloud, and the
delivery pipeline. Design rationale lives in [`02-DESIGN.md`](02-DESIGN.md); the phase-by-phase
evolution is in [`01-ROADMAP.md`](01-ROADMAP.md) and the git tags `phase-0 … phase-11`.

## System overview

```mermaid
flowchart TB
    subgraph client[Client]
        FE["Web client (React SPA)<br/>frontend/index.html"]
    end

    subgraph edge[Edge]
        GW["api-gateway<br/>(Spring Cloud Gateway, WebFlux)<br/>routing + JWT verification + CORS"]
    end

    subgraph cfg[Configuration]
        CS["config-server<br/>(Spring Cloud Config)"]
    end

    subgraph services[Business services - Spring Boot MVC]
        US["user-service<br/>register / login / me<br/>issues JWT (BCrypt)"]
        BS["book-service<br/>catalog CRUD + covers + history"]
        OS["order-service<br/>place / cancel / list"]
        PS["payment-service<br/>pay (1 payment per order)"]
        NS["notification-service<br/>Kafka consumer"]
        AS["analytics-service<br/>Kafka consumer"]
    end

    subgraph data[Database per service - PostgreSQL]
        USDB[(user_db)]
        BSDB[(book_db)]
        OSDB[(order_db)]
        PSDB[(payment_db)]
        NSDB[(notification_db)]
        ASDB[(analytics_db)]
    end

    KAFKA{{"Apache Kafka<br/>order-events / payment-events"}}

    subgraph aws[AWS]
        S3[("S3<br/>book covers")]
        LAMBDA["Lambda<br/>cover-image-lambda"]
        DDB[("DynamoDB<br/>cover metadata +<br/>browsing history")]
        SNS(["SNS<br/>cover-ready topic"])
    end

    FE -->|HTTPS /api/**| GW
    GW --> US & BS & OS & PS

    CS -. serves config .-> US & BS & OS & PS & NS & AS & GW

    US --- USDB
    BS --- BSDB
    OS --- OSDB
    PS --- PSDB
    NS --- NSDB
    AS --- ASDB

    OS -->|Feign + Resilience4j<br/>stock check| BS
    OS -->|OrderPlaced| KAFKA
    PS -->|PaymentCompleted| KAFKA
    KAFKA --> NS
    KAFKA --> AS

    BS -->|presigned PUT/GET| S3
    BS -->|read/write| DDB
    S3 -->|ObjectCreated| LAMBDA
    LAMBDA -->|conditional put| DDB
    LAMBDA -->|publish| SNS
```

**Reading it:** the SPA only ever calls the gateway. The gateway verifies the JWT (shared secret,
never issues) and routes to the four public services. `order-service` calls `book-service`
synchronously (Feign, wrapped in a Resilience4j circuit breaker) for the stock check, and emits an
`OrderPlaced` event; `payment-service` emits `PaymentCompleted`. Two independent consumer groups
(`notification`, `analytics`) react to those events — the write path never blocks on them. Covers are
an async AWS pipeline: the service hands out a presigned S3 URL, and an S3 `ObjectCreated` event
triggers a Lambda that writes cover metadata to DynamoDB and publishes to SNS.

## Order → payment: the core flow

```mermaid
sequenceDiagram
    autonumber
    actor U as User (SPA)
    participant GW as api-gateway
    participant OS as order-service
    participant BS as book-service
    participant K as Kafka
    participant PS as payment-service
    participant NS as notification-service
    participant AS as analytics-service

    U->>GW: POST /api/orders (Bearer JWT)
    GW->>GW: verify JWT, extract user
    GW->>OS: forward + X-User headers
    OS->>BS: Feign getBook(id) [circuit breaker]
    BS-->>OS: price + stock
    OS->>OS: validate stock, persist order (PENDING), @Transactional
    OS-->>K: publish OrderPlaced
    OS-->>GW: 201 OrderResponse
    GW-->>U: 201 order (PENDING)

    U->>GW: POST /api/payments {orderId, amount}
    GW->>PS: forward
    PS->>PS: persist payment (unique orderId), @Transactional
    PS-->>K: publish PaymentCompleted
    PS-->>U: 200 PaymentResponse (SUCCESS)

    K-->>NS: OrderPlaced / PaymentCompleted
    K-->>AS: OrderPlaced / PaymentCompleted
    Note over NS,AS: idempotent consumers<br/>(processed_event ledger keyed by eventId)
```

Consumers are **idempotent**: each records handled `eventId`s in a `processed_event` table and skips
duplicates, so Kafka's at-least-once delivery is safe to re-deliver.

## Runtime & delivery

```mermaid
flowchart LR
    subgraph dev[Developer]
        GIT["git push / PR<br/>(main)"]
    end

    subgraph ci["GitHub Actions (.github/workflows/ci.yml)"]
        T["build-and-test<br/>mvn clean verify<br/>(Testcontainers on Linux Docker)"]
        IMG["build-and-push-images<br/>8 images by commit SHA"]
        DEP["deploy<br/>(gated: production environment)"]
    end

    GHCR[("GHCR<br/>ghcr.io/OWNER/bookstore-*")]

    subgraph k8s["Kubernetes (k8s/)"]
        direction TB
        KGW["api-gateway<br/>Service: LoadBalancer"]
        KSVC["service Deployments<br/>+ readiness/liveness probes<br/>+ HPA (CPU)"]
        KDATA["postgres + kafka<br/>(Stateful-style Deployments)"]
        KCFG["ConfigMap + Secret<br/>(JWT secret, DB creds)"]
    end

    PROM["Prometheus scrape<br/>/actuator/prometheus"]

    GIT --> T --> IMG --> GHCR
    IMG --> DEP
    DEP -->|kubectl set image| KSVC
    GHCR -.pull.-> KSVC
    KGW --> KSVC --> KDATA
    KCFG -.-> KSVC
    KSVC -.metrics.-> PROM
```

A failing test makes `build-and-test` red and **blocks** image build and deploy. Images are tagged by
commit SHA (immutable, traceable); `deploy` is gated behind a protected `production` environment for
manual approval. Every service exposes Micrometer metrics at `/actuator/prometheus`
(see [`MONITORING.md`](MONITORING.md)).

## Key invariants

| Concern | How it's enforced |
|---|---|
| No cross-service DB coupling | Database per service; `orders.user_id`, `order_item.book_id` are bare ids (no FK across services) |
| Edge auth | Gateway verifies JWT; services trust forwarded identity; secret via env/config, never committed |
| Order correctness | `@Transactional` place-order; stock checked via Feign; `book-service` uses optimistic locking (`@Version`) |
| One payment per order | Unique constraint on `payment.order_id` |
| Resilience | Resilience4j circuit breaker on `order → book`; graceful degradation when book-service is down |
| Event safety | At-least-once Kafka + idempotent consumers (`processed_event` ledger keyed by `eventId`) |
| Cloud idempotency | Lambda conditional `PutItem` (`attribute_not_exists(bookId)`) |
| Schema management | Flyway migrations only; `ddl-auto=validate` |
