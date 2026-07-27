# 02 — Design & Data-Layer Decisions

Living design notes. Phase 2 establishes the persistence layer; later phases append
their own sections. Every decision here is "be able to explain why" material.

---

## Phase 2 — Data layer

### Data model

```
author (1) ────< (N) book

author: id (PK), name
book:   id (PK), title, isbn (UQ), price, stock (CHECK >= 0),
        author_id (FK -> author.id), version (optimistic lock), created_at
```

Entities:
- `Book` — `@ManyToOne(fetch = LAZY, optional = false)` to `Author`, `@Version Long version`,
  `@CreationTimestamp Instant createdAt`.
- `Author` — `@OneToMany(mappedBy = "author")` back-reference, LAZY (kept lazy on purpose so
  the N+1 problem is demonstrable and then fixed).

### Schema is owned by Flyway; Hibernate only validates

- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate never creates or alters tables. The schema
  is defined entirely in versioned Flyway scripts (`V1__init.sql`, `V2__seed_authors.sql`).
- **Why:** `ddl-auto=update` is non-deterministic, silently drifts, and can't be code-reviewed or
  rolled back. Flyway migrations are ordered, immutable once shipped, and reproducible across
  environments. `validate` catches entity/DDL drift at startup instead of at runtime.
- Verified: on boot Flyway reports *"Successfully applied 2 migrations … now at version v2"* and the
  app starts with **no** `SchemaManagementException` — i.e. the entity mappings exactly match the
  Flyway DDL (including `Instant`↔`timestamptz`, `BigDecimal`↔`numeric(10,2)`, `@Version`↔`bigint`).

### Indexes and their rationale

| Index | Column | Why |
|---|---|---|
| `idx_book_author_id` | `book(author_id)` | Backs the FK and "books by a given author" lookups (incl. the fetch-join). FK columns are not auto-indexed in PostgreSQL. |
| `idx_book_title` | `book(title)` | Backs catalog search/sort by title — the most common read path for a bookstore. |

`isbn` is covered by the `uq_book_isbn` unique constraint (which creates its own index).

**EXPLAIN ANALYZE evidence** (20,000 rows, `ANALYZE`d):

Equality on the indexed `title` → index scan:
```
EXPLAIN ANALYZE SELECT * FROM book WHERE title = 'Title-12345';

 Index Scan using idx_book_title on book  (cost=0.29..8.30 rows=1 width=69)
                                          (actual time=0.010..0.010 rows=1 loops=1)
   Index Cond: ((title)::text = 'Title-12345'::text)
 Execution Time: 0.021 ms
```

Contrast — a leading-wildcard `LIKE '%…%'` can't use a B-tree, so it falls back to a seq scan:
```
EXPLAIN ANALYZE SELECT * FROM book WHERE title LIKE '%12345%';

 Seq Scan on book  (cost=0.00..497.02 rows=2 width=69) (actual time=0.715..1.361 rows=1 loops=1)
   Filter: ((title)::text ~~ '%12345%'::text)
   Rows Removed by Filter: 20001
```
Takeaway: the B-tree index accelerates equality and prefix (`LIKE 'x%'`) predicates, not
substring search. Substring/full-text search would need a different index type (trigram/GIN) — noted
for later if catalog search grows.

### N+1 problem and fix

- **Where:** listing authors together with their books. Naively, `authorRepository.findAll()` runs
  one query for the authors, then Hibernate lazily loads each author's `books` on first access —
  **1 + N** queries.
- **Fix:** `AuthorRepository.findAllWithBooks()` uses `LEFT JOIN FETCH a.books`, loading everything
  in **one** query.
- **Measured** by `NPlusOneQueryTest` with Hibernate statistics (`generate_statistics=true`):

  | Approach | Query count (3 authors) |
  |---|---|
  | `findAll()` + touch `getBooks()` (naive) | **4** (= authors + 1) |
  | `findAllWithBooks()` (fetch join) | **1** |

- The same pattern protects the book endpoints: `GET /api/books` uses `findAllWithAuthor()`
  (`JOIN FETCH b.author`), observed in the SQL log as a **single** `book JOIN author` select — so
  mapping to a DTO never triggers a per-row lazy load, even with `open-in-view=false`.

> The query counts above are asserted by `NPlusOneQueryTest`; the single-query book listing was
> observed live in the dev-profile SQL log (see Verification status).

### Transactions

- Read methods: `@Transactional(readOnly = true)` — a consistent snapshot and a hint that lets the
  driver/DB skip write bookkeeping.
- Write methods (`createBook`, `updateBook`): `@Transactional` — each does multiple operations
  (uniqueness check, author lookup, insert/update) that must commit or roll back as one unit.
- `spring.jpa.open-in-view=false` — the Hibernate session does **not** stay open into the view layer.
  Fetching is decided explicitly in the service (fetch-join queries), which is why every DTO field is
  available at mapping time without a lazy surprise.

### Optimistic locking

- `Book.version` (`@Version`) makes every update a `… WHERE id = ? AND version = ?`. If another
  transaction already bumped the version, 0 rows match and Hibernate raises
  `ObjectOptimisticLockingFailureException` instead of silently overwriting.
- **Why optimistic, not `SELECT … FOR UPDATE`:** stock contention is low and reads dominate;
  optimistic locking avoids holding row locks and only pays a cost on the rare real conflict.
- `BookRepositoryTest.staleUpdate_failsOptimisticLock` proves it: it loads a book, simulates a
  concurrent update via a native `UPDATE … version = version + 1`, then flushing the stale entity
  throws the optimistic-lock exception.

### Author is a required FK; seeding

- `book.author_id` is `NOT NULL` — every book has an author. Creating a book with an unknown
  `authorId` returns **404** (`Author not found`); the DB FK is the backstop.
- A few reference authors are seeded (`V2__seed_authors.sql`) so the API is usable out of the box.
  Books are never seeded — they go through the API. Author-management endpoints aren't part of the
  documented API surface, so none were added this phase.

### Repository evolution (Phase 1 → 2)

Phase 1's `BookRepository` was a hand-rolled in-memory interface whose method set deliberately
mirrored `JpaRepository`. Phase 2 simply made it `extends JpaRepository<Book, Long>` (plus derived
`existsByIsbn` and two fetch-join queries) and deleted the in-memory implementation — the service
layer's calls were unchanged. The Phase-1 `InMemoryBookRepository`/its test were removed.

### Verification status (honest record)

Run in this environment:
- `BookServiceImplTest` (Mockito, 9 tests) — **pass**.
- App booted against the live docker-compose PostgreSQL: Flyway migrated V1+V2, `validate` passed,
  full CRUD via curl (201/200/204), 404 (unknown author), 409 (duplicate isbn); rows confirmed in
  `psql`. DB constraints verified directly: `uq_book_isbn`, `chk_book_stock_non_negative`,
  `fk_book_author` all reject bad rows. `EXPLAIN ANALYZE` captured above.

Confirmed on the local machine:
- `mvn clean verify` → **BUILD SUCCESS, 18 tests** (via Colima; see `docs/BUGLOG.md` for why Docker
  Desktop 4.83 needed replacing and the `api.version` pin). The Testcontainers tests
  (`BookRepositoryTest` incl. the optimistic-lock case, `NPlusOneQueryTest`, and the `@SpringBootTest`
  smoke test) all pass. The agent's own sandbox cannot run Testcontainers (it force-routes docker-java
  to Docker Desktop), so those were verified on the local machine.

---

## Phase 3 — Authentication & authorization

### Model & roles

- `User` (table `users`): `username` (UQ), `email` (UQ), `password_hash`, `role`, `created_at`.
  Only the **BCrypt hash** is stored — never plaintext. `role` is `USER | ADMIN` (DB CHECK +
  `@Enumerated(STRING)`), surfaced to Spring Security as authority `ROLE_USER` / `ROLE_ADMIN`.
- Self-registration always creates a `USER`; ADMINs are provisioned out of band (no public path to
  self-elevate). Flyway `V3__users.sql` owns the schema.

### Stateless JWT

- Login authenticates via the `AuthenticationManager` (which uses `CustomUserDetailsService` +
  `BCryptPasswordEncoder`), then `JwtUtil` issues an HS256/HS384-signed JWT with `sub=username` and a
  `role` claim, plus `iat` / `exp`.
- Every request runs through `JwtAuthenticationFilter` (a `OncePerRequestFilter` before Spring's
  username/password filter): it reads the `Bearer` token, **verifies the signature and expiry**, and
  builds the `Authentication` straight from the token's claims — **no session, no per-request DB
  lookup**. `SessionCreationPolicy.STATELESS`.
- **How the server validates a token (interview answer):** recompute the HMAC over `header.payload`
  with the server's secret key and compare it to the token's signature (rejects tampering/forgery);
  check `exp` against now (rejects expired); then trust `sub`/`role`. jjwt throws
  `ExpiredJwtException` / signature exceptions, which the filter catches → the request stays
  unauthenticated → 401.

### Authorization rules (SecurityConfig)

| Endpoint | Access |
|---|---|
| `/api/auth/**`, `GET /api/books/**`, `/actuator/health` | PUBLIC |
| `POST/PUT/DELETE /api/books/**` | ADMIN |
| `GET /api/users/me` | any authenticated user |
| `GET /api/users`, `GET /api/users/{id}` | ADMIN |

401 (unauthenticated) and 403 (authenticated but wrong role) return the same structured
`ErrorResponse` JSON as the rest of the API, via a custom `AuthenticationEntryPoint` and
`AccessDeniedHandler` (these fire inside the filter chain, before the `@RestControllerAdvice`).

### Secrets & logging

- The JWT secret and TTL come from the environment (`JWT_SECRET`, `JWT_EXPIRATION_MS`); the yml only
  holds a clearly-labelled dev default. Nothing secret is committed.
- `RegisterRequest`/`LoginRequest` override `toString()` to redact the password, so the AOP logging
  aspect (which logs service-method args) never records credentials; it logs no return values, so
  issued JWTs aren't logged either.

### API-path note

The ROADMAP sketched `GET /api/auth/me`, but the authoritative API list (`00-project-overview.md`)
uses `GET /api/users/me` plus the admin `GET /api/users` / `/api/users/{id}`. Implemented the latter
to match the final contract and reduce Phase 5 rework.

---

## Phase 5 — Microservices split

### Topology

The monolith became four independently-deployable Spring Boot services plus a shared `common`
library, under `bookstore-platform/` (Maven multi-module):

- **user-service** (8081) — auth; owns `userdb`. Issues JWTs.
- **book-service** (8082) — catalog; owns `bookdb`. Validates JWTs (reads public, writes ADMIN).
- **order-service** (8083) — orders; owns `orderdb`. Calls book-service.
- **payment-service** (8084) — payments; owns `paymentdb`.
- **common** — JWT filter/util, error handling, logging aspect. Each service `scanBasePackages="com.bookstore"`.

**Database per Service.** Each service has its own database; there are **no cross-service foreign
keys**. `order_item.book_id`, `orders.owner_username`, and `payment.order_id` are bare identifiers.
Intra-service FKs (e.g. `order_item → orders`) are fine. To read another service's data, call its API.

### Stateless JWT propagation

user-service signs a token with the shared `JWT_SECRET`; every service validates it with the same
secret via the common `JwtAuthenticationFilter` — no session, no shared session store, no per-request
DB lookup (role comes from the token claim). order-service forwards the incoming `Authorization`
header downstream via a Feign `RequestInterceptor`, so identity is preserved across the call chain.

### Resilient service-to-service calls (order → book)

order-service fetches price/stock from book-service with an OpenFeign client, wrapped in a
Resilience4j circuit breaker with explicit connect/read timeouts (2s). The `BookClientFallback`
distinguishes two failure modes:

- book-service returns **404** (book genuinely missing) → `ResourceNotFoundException` → 404.
- book-service is **down / slow / circuit open** → `CatalogUnavailableException` → **503**.

**Acceptance:** with book-service unreachable, placing an order returns a fast 503 instead of hanging
or cascading timeouts — verified automatically by `OrderResilienceIntegrationTest`.

### Saga / eventual consistency (order + stock + payment)

Placing and paying for an order spans three services with **no single ACID transaction**. Phase 5
does the synchronous read-and-validate (Feign) and creates the order `PENDING`; the asynchronous,
event-driven Saga below is designed here and implemented in **Phase 7 (Kafka)**.

Choreographed Saga (happy path):

1. **order-service**: validate items, create order `PENDING`, publish `OrderPlaced{orderId, items}`.
2. **book-service**: consume `OrderPlaced` → reserve/decrement stock (optimistic lock). If short,
   publish `StockRejected{orderId}`.
3. **payment-service**: on customer payment, record it, publish `PaymentCompleted{orderId}`.
4. **order-service**: consume `PaymentCompleted` → order `PAID`.

Compensation (failure paths):

- `StockRejected` → order-service marks the order `CANCELLED` (nothing to refund yet).
- Payment fails / times out → order-service marks the order `CANCELLED` and publishes
  `OrderCancelled`; book-service consumes it and **releases the reserved stock** (increments back).

Guarantees: consumers must be **idempotent** (Kafka is at-least-once), messages keyed by `orderId`
for per-order ordering. This keeps the system **eventually consistent**: an order is never `PAID`
without stock reserved, and stock is never held for a cancelled order.

### Local run

`bookstore-platform/docker-compose.yml` starts one PostgreSQL with the four per-service databases
(`docker/init-databases.sql`). Services run via `mvn spring-boot:run` (each has its own port and DB
defaults). Containerizing the services into the compose file is Phase 10.

---

## Phase 6 — Centralized configuration (Spring Cloud Config)

### Setup

- **config-server** (`@EnableConfigServer`, port 8888) runs in the `native` profile and serves the
  files under `config-server/src/main/resources/config-repo/`:
  - `application.yml` — shared config for **all** services (JPA `validate`, `open-in-view=false`,
    Flyway, JWT secret/TTL, management, logging) — the config that used to be copy-pasted into every
    service's local yml.
  - `user-service.yml` / `book-service.yml` / `order-service.yml` / `payment-service.yml` — per-service
    (port, datasource, and order-service's Feign/Resilience4j settings).
- Each service imports it with `spring.config.import: optional:configserver:${CONFIG_SERVER_URL:...}`
  and the `spring-cloud-starter-config` client. Imported config **overrides** the service's local
  `application.yml`. A `bookstore.config-source` marker exists only in the config-repo, so
  `/actuator/env` shows whether a value came from the config-server.

### Two deliberate choices

- **`optional:` import + local fallback.** The import is optional and each service keeps a full local
  `application.yml`. So a service (and every test) still boots if the config-server is down — it
  degrades to local defaults instead of failing. When the config-server is up, it wins. This also keeps
  the test suites independent of a running config-server.
- **No secrets in the config-repo.** Secrets are `${ENV}` placeholders (`JWT_SECRET`, `*_DB_PASSWORD`)
  resolved from the environment at bind time — the repo only ever contains placeholders and local-dev
  defaults, never real credentials.

### How this maps to Kubernetes (ConfigMap / Secret)

In a real K8s deployment the config-server + config-repo are typically **replaced by native K8s
objects**:

- Non-secret configuration → a **ConfigMap** per service (or a shared one), mounted as environment
  variables or as an `application.yml` file into the pod.
- Credentials (DB passwords, `JWT_SECRET`) → a **Secret**, injected as env vars. Secrets are stored
  separately from ConfigMaps, can be encrypted at rest, and locked down with RBAC — which is exactly
  why we already keep secrets as `${ENV}` placeholders rather than in the config files.

Because the app reads everything through Spring's `${ENV}`/property mechanism, **the application code
doesn't change** — only *where* the values come from (config-server vs ConfigMap/Secret). A team might
also keep a Git-backed config-server for dynamic refresh (`@RefreshScope` + Spring Cloud Bus), but the
ConfigMap/Secret approach is the K8s-native default and needs no extra infrastructure.

---

## Phase 7 — Kafka (event-driven, eventually consistent)

### Flow

- **Producers:** order-service publishes `OrderPlaced` on `order-placed`; payment-service publishes
  `PaymentCompleted` on `payment-completed`. Both are keyed by **orderId**, so all events for one
  order land on the same partition and are consumed in order. Publishing is **non-blocking** (a broker
  hiccup never fails the HTTP request).
- **Consumers (two groups):** notification-service (group `notification-service`) "sends"
  notifications; analytics-service (group `analytics-service`) aggregates running totals. Because they
  use **different consumer groups**, each independently receives **every** event off the same topics.
- Events live in `common` (`OrderPlacedEvent`, `PaymentCompletedEvent`) and are JSON-serialized with
  type headers; consumers trust `com.bookstore.common.event`.

### At-least-once + idempotency

Kafka is at-least-once, so a consumer can see the same record more than once (redelivery, rebalance,
retry). Each event carries a stable `eventId` (a UUID the producer sets once). Consumers keep a
`processed_event` ledger keyed by `eventId`: before doing the work they check the ledger and skip if
already present, then record it in the same transaction. `NotificationIdempotencyTest` /
`AnalyticsIdempotencyTest` prove it — the same event delivered twice yields exactly one side effect.

### Consistency model

This realizes the Phase-5 Saga: an order is created `PENDING`; downstream reactions happen
asynchronously via events, so the system is **eventually consistent** rather than transactionally
consistent across services. The known gap is the **dual write** (DB commit + Kafka publish aren't
atomic); the production-grade fix is the **transactional outbox** pattern (write the event to an
outbox table in the same DB transaction, relay it to Kafka separately) — tracked in BACKLOG.

### Dead-letter queue (challenge — deferred)

Design: wrap consumer deserialization in `ErrorHandlingDeserializer` (so a poison/unparseable message
doesn't hot-loop) and register a `DefaultErrorHandler` with a bounded backoff plus a
`DeadLetterPublishingRecoverer` that routes exhausted records to a `<topic>.DLT` topic; monitor DLT
lag/size as an alert. Per BACKLOG this lands after the Phase 7 main flow is validated, to keep the
verified idempotent-consumer core stable.

### Local run

`docker compose up -d` starts PostgreSQL (per-service DBs) and a single-node **Kafka** (KRaft, no
ZooKeeper) advertised on `localhost:9092`. Run the six services with `mvn spring-boot:run`; place an
order and pay, then watch notification-service and analytics-service both react in their logs.

---

## Phase 8 — API Gateway

### Single entry point + routing

Spring Cloud Gateway runs on **:8080** as the one address clients use; the services move behind it on
8081–8084. Routes are pure path predicates → service URIs (no business logic in the gateway):

| Path | Service |
|---|---|
| `/api/auth/**`, `/api/users/**` | user-service |
| `/api/books/**` | book-service |
| `/api/orders/**` | order-service |
| `/api/payments/**` | payment-service |

### Reactive gateway ⇒ no dependency on `common`

Spring Cloud Gateway is **WebFlux** (reactive); putting `spring-boot-starter-web` (servlet MVC) on its
classpath makes Boot fail ("Spring MVC found on classpath, incompatible with Spring Cloud Gateway").
Since `common` pulls servlet web + the servlet `JwtAuthenticationFilter`, the gateway **deliberately
does not depend on `common`**. It re-implements JWT verification with jjwt (~10 lines,
`GatewayJwtValidator`) using the same `JWT_SECRET`. Small duplication, but it keeps the reactive
gateway clean. (config-server is likewise skipped here to avoid pulling servlet bits.)

### Edge authentication + identity propagation

`EdgeAuthenticationFilter` is a `GlobalFilter` (order −1, before routing):

- **Public:** `/api/auth/**`, `GET /api/books/**`, `/actuator/**` → pass through.
- **Protected:** everything else must carry a valid `Bearer` JWT, else **401 at the edge** (the
  request never reaches a service). Verified by `EdgeAuthenticationIntegrationTest`.
- On success it forwards the bearer token **and** adds `X-Auth-Username` / `X-Auth-Role` headers.

**Defense in depth:** the gateway only checks "is this a valid token"; each service still runs its own
JWT filter and does the **fine-grained role checks** (e.g. book writes = ADMIN → 403). So a
compromised/misconfigured gateway can't bypass per-service authorization.

### CORS

Configured once, centrally, in the gateway's `globalcors` (open for local dev; lock origins down for
production) — clients only ever talk to the gateway, so CORS lives in exactly one place.

---

## Phase 9 — AWS (S3 / Lambda / DynamoDB / SNS-SES)

Region us-east-1; credentials via the SDK default chain (never hardcoded). Full deployment steps in
`docs/AWS-DEPLOYMENT.md`. Both features live in book-service (+ a standalone Lambda).

### Feature B — browsing history (DynamoDB)

A logged-in `GET /api/books/{id}` records a view **asynchronously** (`@Async`, best-effort — never
slows or fails the lookup) to DynamoDB `UserBrowsingHistory` (PK=userId, SK=viewedAt epoch-ms, plus an
`expireAt` TTL attribute for ~30-day expiry). `GET /api/books/me/history` queries by user with
`scanIndexForward(false)` for newest-first. Anonymous reads are not recorded.

### Feature A — cover pipeline (S3 → Lambda → DynamoDB → SNS/SES)

- Upload uses a **presigned S3 PUT URL** (`POST /api/books/{id}/cover`, ADMIN): the client uploads
  straight to S3, so image bytes never flow through the service. The key is deterministic:
  `covers/{bookId}`.
- The `CoverImageHandler` Lambda reacts to the `s3:ObjectCreated` event, reads size/content-type/
  dimensions, writes `CoverMetadata` (PK=bookId), and publishes an SNS "cover processed" message
  (SNS → email subscription via SES).
- `GET /api/books/{id}/cover` (public) reads `CoverMetadata`.

**Idempotency:** the Lambda writes with `PutItem` + `attribute_not_exists(bookId)` and publishes SNS
**only** on a first-time write. S3/Lambda are at-least-once, so a redelivered event fails the
conditional write and is skipped — no duplicate metadata row and no duplicate email. The deterministic
`bookId` key is what makes this work.

### Testable vs real-AWS

- Tested locally: browsing-history read/write and cover-metadata read against **`amazon/dynamodb-local`**
  (Testcontainers); presigned-URL generation as an **offline** unit test; the Lambda's idempotent
  conditional write + SNS-publish with **mocked** AWS clients.
- Real AWS only: the S3 event → Lambda → SNS/SES wiring (validated by deploying to an account —
  LocalStack can't run on this project's Colima engine, see BUGLOG).

---

## Phase 10 — Containerization & orchestration

### Image build (one parameterized multi-stage Dockerfile)

Rather than eight near-identical Dockerfiles, a single `Dockerfile` takes a `SERVICE` build-arg:
stage 1 (`maven:3.9-eclipse-temurin-17`) runs `mvn -pl ${SERVICE} -am -DskipTests package` (builds the
module + the common library); stage 2 (`eclipse-temurin:17-jre`) copies just the jar and runs it as a
non-root user. `.dockerignore` keeps `target/` and git out of the context. docker-compose/k8s pass the
`SERVICE` per service, so each still gets its own image from the same JDK-build → JRE-run pipeline.

### docker-compose — one-command full stack (the DoD)

`docker compose up --build` brings up PostgreSQL (a database per service via the init script), a
single-node Kafka, and all eight services. Key points:
- **Kafka dual listener:** `INTERNAL://kafka:9092` for in-network services and `EXTERNAL://localhost:29092`
  for host tools — the classic fix so the advertised address is correct for both callers.
- Services reach each other by compose service name (`postgres:5432`, `kafka:9092`,
  `http://book-service:8082`, `http://config-server:8888`); the **gateway is the only host-exposed
  port (:8080)**.
- Each JVM heap is capped (`-Xmx256m`) so the whole stack fits a modest Docker VM; `depends_on` waits
  for Postgres health.

### Kubernetes (`k8s/`)

- **ConfigMap** (non-secret: URLs, DB hosts/users, region) + **Secret** (JWT + DB passwords), injected
  into every pod via `envFrom`. All services listen on **8080** and resolve each other by Service DNS.
- One **Deployment + Service** per service (uniform template — differ only by name/image), plus
  PostgreSQL (emptyDir for the demo; a StatefulSet+PVC in prod) and Kafka.
- **Liveness/readiness probes → `/actuator/health`**, so k8s only routes to ready pods and restarts
  wedged ones.
- **HPA** (challenge) on book- and order-service: scale 1→4 on 70% CPU (needs metrics-server).
- The gateway Service is `LoadBalancer` (the single external entry). Notes in `k8s/README.md` cover
  loading images into kind/minikube, secret management, and IRSA for the AWS-backed features.

This maps back to the Phase-6 note: in k8s the config-server/config-repo is replaced by the ConfigMap
(non-secret) and Secret (credentials) — the app reads both through the same `${ENV}` placeholders, so
no code changes.

---

## Phase 11 — CI/CD & monitoring

### Pipeline (`.github/workflows/ci.yml`)

On every push/PR to `main`:
1. **build-and-test** — `mvn clean verify` on an Ubuntu runner. Testcontainers works **natively** here
   (real Linux Docker), so the same suite that needed Colima locally just runs. A failing test fails
   this job → the pipeline goes **red** (verified: temporarily disabling the duplicate-ISBN check turned
   `createBook_duplicateIsbn_throwsDuplicate` red, then reverted).
2. **build-and-push-images** (push to main only, after tests pass) — a matrix builds one image per
   service from the shared Dockerfile and pushes to **GHCR** tagged by the commit SHA (+ `latest`).
3. **deploy** — gated behind a GitHub **Environment (`production`) with required reviewers** (manual
   approval); the step runs `kubectl apply` + `set image` when a `KUBE_CONFIG` secret is present, else
   prints the rollout it would perform.

Images are tagged by SHA so a deploy is an immutable, traceable artifact and rollbacks are a
`set image` to a previous SHA.

### Monitoring

Every service exposes **`/actuator/prometheus`** (Micrometer added to common + config-server +
gateway; exposure centralized in the config-repo). A Prometheus server scrapes each pod. Metrics that
matter and their alert thresholds — the four golden signals (QPS, error rate, p99, saturation) plus
per-service specifics (HikariCP DB pool, Resilience4j circuit-breaker state, Kafka consumer lag) — are
defined in **`docs/MONITORING.md`**. `/actuator/health` drives the k8s probes; the metric alerts catch
the "UP but unhealthy" cases (bad latency/error rate) that health can't.
