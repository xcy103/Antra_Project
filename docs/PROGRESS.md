# Progress Log

> Append one entry per completed Phase. Format: date / Phase / what was done / what's left / next step.

## 2026-07-24 — Phase -1: Planning

- Finished reading `capstone-project.docx`; produced `00-project-overview.md`, `CLAUDE.md`, `01-ROADMAP.md`
- Tech stack fixed as Java 17 + Maven + Spring Boot 3.x + Docker PostgreSQL
- Left open: `02-DESIGN.md` to be created when Phase 1 starts
- Next step: Phase 0 environment & repo bootstrap (**start after manual confirmation**)

## 2026-07-24 — Housekeeping

- Created the remote public GitHub repo and pushed the planning docs
- Converted all repo content to English; from now on the repo is English-only (docs, code, comments, commits)

## 2026-07-24 — Phase 0: Environment & repo bootstrap

- Done: Spring Boot 3.4.1 monolith skeleton under `bookstore/` (web + actuator only); `application.yml` exposing the health endpoint; `docker-compose.yml` for a single PostgreSQL 16; `.env.example`
- Tests: `BookstoreApplicationTests` — context loads + `/actuator/health` returns UP (real HTTP assertion, not an empty test)
- DoD verified locally (all three green): `mvn clean verify` → BUILD SUCCESS (2 tests); `curl /actuator/health` → `{"status":"UP"}`; `docker compose up -d postgres` → container healthy, `pg_isready` accepting connections on 5432
- Environment decision: this project uses Docker for PostgreSQL only. The machine's native EDB PostgreSQL 17 (which had occupied 5432) was stopped so the Docker container can own the port; other native dev services (colima, kafka, mysql via brew) were already stopped.
- No business code, entities, or tables (correct for Phase 0). No next-phase dependencies introduced.
- Next step: Phase 1 (monolith skeleton — Book CRUD + AOP), **after manual confirmation**

## 2026-07-24 — Phase 1: Monolith skeleton (Book CRUD + AOP)

- Done: layered `controller / service(+impl) / repository(+impl) / entity / dto / exception / aop`; `Book` domain entity; `BookRequestDto` (Bean Validation) + `BookResponseDto`; 5 CRUD endpoints under `/api/books` (all PUBLIC this phase); `GlobalExceptionHandler` mapping 400/404/409 to a structured `ErrorResponse`; `LoggingAspect` (`@Around` on the service layer, logs args + elapsed ms); dev/prod profiles
- Design decision: `BookRepository` is an in-memory (ConcurrentHashMap) implementation, no JPA/DB/Flyway yet. Its method set mirrors `JpaRepository` so Phase 2 can swap in a JPA + PostgreSQL implementation without touching the service layer. Rationale: ROADMAP defers PostgreSQL+Flyway to Phase 2, and CLAUDE.md §6 forbids `ddl-auto` schema management — so no real DB in Phase 1, and no unplanned H2 dependency.
- Tests: `BookServiceImplTest` (Mockito, 8 tests — happy paths + duplicate-ISBN 409 + not-found 404 on get/update/delete) and `InMemoryBookRepositoryTest` (5 tests). Phase 0 smoke tests retained. `mvn clean verify` → BUILD SUCCESS, 15 tests green.
- DoD verified locally: all 5 endpoints exercised via curl (201/200/204); 404/400/409 return structured JSON (400 includes per-field `fieldErrors`); AOP timing logs visible (incl. `✗` on failure). Controller depends only on the service interface (no repository); entities never appear in controller signatures; constructor injection throughout.
- Deferred: sensitive-argument masking in `LoggingAspect` → BACKLOG (Phase 3). No Security/JPA/Flyway/Kafka/Feign/Gateway/Dockerfile introduced (correct for Phase 1).
- Next step: Phase 2 (data layer — PostgreSQL + Flyway + Author + indexes + N+1 fix + optimistic lock), **after manual confirmation**

## 2026-07-25 — Phase 2: Data layer (PostgreSQL + Flyway + JPA)

- Done: switched to Docker PostgreSQL with `ddl-auto=validate`; `Book` is now a JPA entity with a LAZY `@ManyToOne` to the new `Author` entity, `@Version` optimistic locking, and `@CreationTimestamp`; Flyway `V1__init.sql` (tables, `uq_book_isbn`, `chk_book_stock_non_negative`, FK, `idx_book_author_id`, `idx_book_title`) + `V2__seed_authors.sql`; repositories now Spring Data JPA with fetch-join queries; service methods `@Transactional` (read-only for reads), author resolution with 404; `open-in-view=false`. DTOs carry author fields. Full design write-up in `docs/02-DESIGN.md`.
- Repo evolution: in-memory `BookRepository` impl (+ its test) removed; `BookRepository extends JpaRepository` as planned in Phase 1 — service layer unchanged.
- Tests: `BookServiceImplTest` (Mockito, 9) updated for JPA; new `BookRepositoryTest` (persist/read-back, unique, CHECK, **optimistic lock**) and `NPlusOneQueryTest` (asserts naive = authors+1, fetch-join = 1 query) via `@DataJpaTest` + Testcontainers; smoke test now boots against Testcontainers PostgreSQL.
- Verified in agent sandbox: unit tests pass; app booted against live docker-compose PG → Flyway applied V1+V2, `validate` passed, CRUD via curl (201/200/204), 404 (unknown author), 409 (dup isbn), rows confirmed in psql; DB constraints (unique/CHECK/FK) reject bad rows; `EXPLAIN ANALYZE` shows `Index Scan using idx_book_title`; book list is a single JOIN query.
- Verified on the local machine: `mvn clean verify` → **BUILD SUCCESS, 18 tests** (all Testcontainers tests included). Getting there needed a Docker-tooling fix: Docker Desktop 4.83's API-proxy rejects docker-java, and docker-java defaults to Docker API 1.32 while the local engine requires ≥1.40. Resolution: run tests against **Colima** (`~/.testcontainers.properties` → Colima socket) with `api.version=1.41` pinned in the Surefire config. Two real test bugs were fixed along the way (IDENTITY eager-insert assertion target; N+1 test isolation from seed data). Full STAR write-up in `docs/BUGLOG.md`.
- Boundary: no security/users, no order/payment tables, no `ddl-auto: update` fallback (correct for Phase 2).
- Next step: Phase 3 (auth & security — User, register/login, BCrypt, JWT, role-based access), **after manual confirmation**

## 2026-07-25 — Phase 3: Authentication & authorization

- Done: `User` entity + `Role` enum + Flyway `V3__users.sql`; register/login (`AuthController`) and `GET /api/users/me` + admin `GET /api/users` / `/api/users/{id}` (`UserController`); BCrypt (`PasswordEncoderConfig`); `JwtUtil` (issue/verify, expiry), `JwtAuthenticationFilter`, `CustomUserDetailsService`; stateless `SecurityConfig` (catalog reads + auth public, catalog writes + admin-user endpoints ADMIN); structured 401/403 via custom entry-point/handler; JWT secret & TTL from env. Design write-up in `docs/02-DESIGN.md`.
- Security hardening: `RegisterRequest`/`LoginRequest` redact the password in `toString()` so the AOP aspect never logs credentials (closes the Phase-3 BACKLOG item). Bad login → 401 without revealing which field was wrong.
- API decision: used `/api/users/me` + `/api/users` (per the authoritative API list) rather than the ROADMAP's `/api/auth/me`.
- Tests: `JwtUtilTest` (round-trip, expired rejected, wrong-secret rejected), `AuthServiceImplTest` (Mockito: hashing, duplicate username/email → 409, login issues token), `SecurityIntegrationTest` (Testcontainers: register→login→me, 401 no token, 403 USER-on-ADMIN, 200 ADMIN-on-ADMIN, expired → 401, bad creds → 401, password stored as `$2` hash).
- Verified in sandbox: 16 unit tests pass (`mvn test` for the non-Docker classes); full app run against live PostgreSQL demonstrated register/login/me + 401/403 on protected/admin/book-write endpoints + bad-creds 401 + BCrypt hash in DB. **Testcontainers `SecurityIntegrationTest` pending local `mvn clean verify`** (agent sandbox can't run Testcontainers; now solvable via Colima — see BUGLOG).
- Boundary: no OAuth2 (BACKLOG), no Kafka/Feign/Gateway; JWT secret not hardcoded.
- Next step: Phase 4 (testing suite — `@WebMvcTest`, `@DataJpaTest`, end-to-end integration, JaCoCo), **after manual confirmation**

## 2026-07-25 — Phase 4: Testing suite

- Filled out the test pyramid across all four layers:
  - **Controller (`@WebMvcTest`, filters off):** `BookControllerTest`, `AuthControllerTest`, `UserControllerTest` — request mapping, Bean Validation → 400 with field errors, and exception→status mapping (404/409/401). Authorization itself stays in the integration tests.
  - **Service (Mockito):** added `UserServiceImplTest` (alongside existing `BookServiceImplTest`, `AuthServiceImplTest`).
  - **Repository (`@DataJpaTest` + Testcontainers):** added `UserRepositoryTest` (alongside `BookRepositoryTest`, `NPlusOneQueryTest`).
  - **Integration (`@SpringBootTest` + Testcontainers):** added `EndToEndIntegrationTest` — the required E2E: register → login → (ADMIN) create book → public GET, plus USER-create → 403. Security suite already present.
- JaCoCo wired in (`prepare-agent` + `report` on `verify`); report at `target/site/jacoco/index.html`.
- **Break-a-test verification (DoD):** temporarily disabled the duplicate-ISBN check in `BookServiceImpl.createBook`; `BookServiceImplTest.createBook_duplicateIsbn_throwsDuplicate` turned **red** (expected `DuplicateResourceException`, got a different outcome). Reverted → green. Confirms tests catch broken business logic. No break markers remain in source.
- Verified in sandbox: **38 non-Docker tests green** (`@WebMvcTest` + all Mockito + `JwtUtilTest`). The `@DataJpaTest`/`@SpringBootTest` (Testcontainers) classes and JaCoCo run in the full `mvn clean verify` — **pending local run** (agent sandbox can't run Testcontainers; use Colima per BUGLOG).
- Valuable gotcha recorded in `docs/BUGLOG.md`: `@AutoConfigureMockMvc(addFilters=false)` still instantiates filter beans, so `JwtAuthenticationFilter`'s `JwtUtil` dependency had to be mocked in the slice.
- Next step: Phase 5 (split into microservices), **after manual confirmation**

## 2026-07-26 — Phase 5: Split into microservices

- Monolith → four services + a shared `common` library under `bookstore-platform/` (Maven multi-module, parent aggregator): **user-service** (8081), **book-service** (8082), **order-service** (8083), **payment-service** (8084). Structural choices confirmed with the user: multi-module + common; replace the monolith (its history stays in the `phase-1`…`phase-4` tags).
- **Database per Service**: each service owns its own database; no cross-service FKs (`orders.owner_username`, `order_item.book_id`, `payment.order_id` are bare ids). `docker-compose.yml` starts one PostgreSQL with the four databases via `docker/init-databases.sql`.
- **JWT propagation**: user-service issues tokens with the shared `JWT_SECRET`; every service validates them via the common `JwtAuthenticationFilter` (stateless, role from the claim). order-service forwards the `Authorization` header downstream via a Feign `RequestInterceptor`.
- **Resilient order → book**: OpenFeign client + Resilience4j circuit breaker with explicit 2s timeouts and a fallback that distinguishes 404 (book missing) from service-down (→ 503). **Acceptance automated** by `OrderResilienceIntegrationTest`: with book-service unreachable, placing an order returns a fast 503 instead of hanging.
- **Saga / eventual consistency** (order + stock + payment) designed in `docs/02-DESIGN.md`; the async event-driven implementation + stock mutation/compensation is Phase 7 (Kafka). Phase 5 does the synchronous validate + create `PENDING`.
- Tests per service (unit + `@WebMvcTest` where relevant + `@DataJpaTest` + `@SpringBootTest` via Testcontainers). Sandbox-verified the non-Docker tests (compile + unit/web across all services); full Testcontainers suite pending the user's `mvn clean verify` at `bookstore-platform/`.
- Monolith `bookstore/` and its root `docker-compose.yml`/`.env.example` removed.
- Boundary: no Kafka (Phase 7), no Gateway (Phase 8), no Config Server (Phase 6); services don't share a DB.
- Next step: Phase 6 (Spring Cloud Config), **after manual confirmation**

## 2026-07-26 — Phase 6: Centralized configuration (Spring Cloud Config)

- Added a **config-server** module (`@EnableConfigServer`, port 8888, `native` profile) serving a **config-repo** (`config-server/src/main/resources/config-repo/`): a shared `application.yml` (the JPA/Flyway/JWT/management/logging config that was duplicated across services) plus one file per service (port, datasource, and order-service's Feign/Resilience4j).
- Each of the four services imports config via `spring.config.import: optional:configserver:${CONFIG_SERVER_URL:...}` + `spring-cloud-starter-config`. Imported config overrides the service's local yml; a `bookstore.config-source` marker (only in the config-repo) makes the origin visible in `/actuator/env`.
- **`optional:` + local fallback**: services and all test suites still boot if the config-server is down (degrade to local defaults), so tests stay independent of a running config-server. **No secrets in the config-repo** — `JWT_SECRET`/`*_DB_PASSWORD` remain `${ENV}` placeholders.
- Design doc adds the required paragraph on how this maps to K8s **ConfigMap** (non-secret config) / **Secret** (credentials) — since the app reads via `${ENV}` placeholders, only the config *source* changes, not the code.
- Verified in sandbox (non-Docker): `ConfigServerApplicationTests` (config-server boots and serves user-service config incl. both markers) + user-service `@WebMvcTest` slice tests still green with the config import present (optional import skipped gracefully). Full `mvn clean verify` across all modules pending on the local machine.
- Boundary: no Gateway (Phase 8), no Kafka (Phase 7); config-server not secured/HA yet (fine for this phase).
- Next step: Phase 7 (Kafka async: OrderPlaced/PaymentCompleted, notification + analytics consumers), **after manual confirmation**

## 2026-07-26 — Phase 7: Kafka (event-driven async)

- Events in `common` (`OrderPlacedEvent`, `PaymentCompletedEvent`, `KafkaTopics`). order-service publishes `OrderPlaced` on placement; payment-service publishes `PaymentCompleted` on success — both keyed by `orderId` (per-order ordering), non-blocking so a broker hiccup never fails the request.
- Two new consumer services, **no business API** (health only), Spring Security auto-config excluded: **notification-service** (group `notification-service`, "sends" notifications) and **analytics-service** (group `analytics-service`, aggregates running totals in a `metric` table). Different groups → both receive every event.
- **Idempotency** (Kafka is at-least-once): each consumer keeps a `processed_event` ledger keyed by `eventId`; the work is skipped if already recorded. Proven by `NotificationIdempotencyTest` and `AnalyticsIdempotencyTest` (same event twice → one side effect) using Testcontainers Kafka + Postgres. Unit tests cover the dedup/aggregation logic without a broker; publisher unit tests verify topic+key.
- Infra: `docker-compose.yml` adds a single-node **Kafka** (KRaft, no ZooKeeper) on `localhost:9092`, plus `notificationdb`/`analyticsdb`. Kafka `bootstrap-servers` centralized in the config-repo shared file.
- Test-infra: Ryuk disabled in the Surefire config (`TESTCONTAINERS_RYUK_DISABLED=true`) — Colima can't bind-mount its socket into Ryuk (see BUGLOG); now plain `mvn clean verify` works with no env var.
- Deferred (challenge, per BACKLOG): **DLQ** (ErrorHandlingDeserializer + DefaultErrorHandler + DeadLetterPublishingRecoverer) and the **transactional outbox** for exactly-once producing — designs written in `docs/02-DESIGN.md`, to follow now that the main flow works.
- Boundary: no Gateway (Phase 8), no AWS (Phase 9). Consumers expose only `/actuator/health`.
- Next step: Phase 8 (API Gateway — Spring Cloud Gateway, edge JWT, routing, CORS), **after manual confirmation**

## 2026-07-26 — Phase 8: API Gateway

- Added **api-gateway** (Spring Cloud Gateway, reactive) as the single entry point on :8080; routes by path to user/book/order/payment (8081–8084). No business logic in the gateway.
- **Edge JWT validation**: `EdgeAuthenticationFilter` (GlobalFilter, pre-routing) rejects unauthenticated requests to protected routes with 401 before they reach a service; public routes are `/api/auth/**`, `GET /api/books/**`, actuator. Forwards the token + adds `X-Auth-Username`/`X-Auth-Role`. Fine-grained role checks stay in the services (defense in depth).
- The reactive gateway **doesn't depend on common** (servlet MVC vs WebFlux conflict); it re-implements JWT verification with jjwt (`GatewayJwtValidator`) using the shared secret. CORS centralized in the gateway.
- Tests (no Docker needed): `GatewayJwtValidatorTest` (valid/expired/wrong-signature) + `EdgeAuthenticationIntegrationTest` (WebTestClient: 401 for protected-without/with-invalid-token and catalog writes; pass-through for public auth/catalog-read and valid-token routes). Verified green in the agent sandbox.
- Boundary: no business logic in gateway; no AWS (Phase 9).
- Next step: Phase 9 (AWS — S3/Lambda/DynamoDB cover pipeline + browsing history), **after manual confirmation**

## 2026-07-26 — Phase 9: AWS (S3 / Lambda / DynamoDB / SNS-SES)

- **Feature B — browsing history:** logged-in `GET /api/books/{id}` asynchronously writes DynamoDB `UserBrowsingHistory` (PK=userId, SK=viewedAt, 30-day TTL); `GET /api/books/me/history` returns newest-first. `@Async` + best-effort (never fails the lookup); anonymous reads not recorded. Tested against `amazon/dynamodb-local`.
- **Feature A — cover pipeline:** `POST /api/books/{id}/cover` (ADMIN) returns a **presigned S3 PUT URL** (deterministic key `covers/{bookId}`); the new **`cover-image-lambda`** module reacts to the S3 event, writes `CoverMetadata` with a conditional `attribute_not_exists(bookId)` put and publishes SNS **only on first write** (idempotent — no duplicate row/email); `GET /api/books/{id}/cover` (public) reads it.
- Testing (LocalStack can't run on Colima — see BUGLOG, so used narrower locals): DynamoDB via `amazon/dynamodb-local`; presigned-URL generation as an offline unit test; Lambda idempotency via mocked S3/DynamoDB/SNS clients. Real S3→Lambda→SNS/SES wiring is deployed to a real account per `docs/AWS-DEPLOYMENT.md`.
- AWS SDK v2 (BOM-managed), region us-east-1, SDK default credential chain (endpoints overridable for dev/test); full deploy runbook in `docs/AWS-DEPLOYMENT.md`.
- Boundary: no Docker/K8s yet (Phase 10). Consumers unchanged.
- Next step: Phase 10 (containerization — Dockerfiles, docker-compose full stack, K8s manifests), **after manual confirmation**

## 2026-07-27 — Phase 10: Containerization & orchestration

- **Multi-stage Dockerfile** (one file, parameterized by `SERVICE`): JDK-17 Maven build → JRE-17 runtime, non-root; `.dockerignore` trims the context. Validated by building an image in the sandbox.
- **docker-compose full stack** (the DoD): PostgreSQL (db per service), single-node Kafka with a dual listener (`kafka:9092` internal / `localhost:29092` host), all 8 services built from the shared Dockerfile, gateway the only exposed port (:8080). JVM heaps capped so it fits a modest VM. `docker compose config` valid.
- **k8s manifests** (`k8s/`): namespace; ConfigMap (non-secret) + Secret (JWT/DB creds) via `envFrom`; PostgreSQL + Kafka; 8 Deployments+Services (uniform, all on 8080, DNS discovery); Actuator **liveness/readiness probes**; gateway as `LoadBalancer`; **HPA** (challenge) on book/order (1→4 @ 70% CPU). All YAML parses; full schema validation happens on a real cluster (+ `k8s/README.md` deploy guide).
- Not containerized: `common` (library) and `cover-image-lambda` (deployed to AWS Lambda, not a service).
- Boundary: no CI/CD yet (Phase 11).
- Next step: Phase 11 (CI/CD with GitHub Actions + monitoring), **after manual confirmation**

## 2026-07-27 — Phase 11: CI/CD & monitoring

- **GitHub Actions** (`.github/workflows/ci.yml`): (1) `build-and-test` runs `mvn clean verify` on Ubuntu — Testcontainers runs natively there (no Colima/Ryuk workarounds needed); a failing test makes the pipeline red. (2) `build-and-push-images` — matrix builds one image per service from the shared Dockerfile and pushes to GHCR tagged by commit SHA (+ latest), only after tests pass. (3) `deploy` — gated behind a `production` GitHub Environment (manual approval); runs `kubectl apply`/`set image` when `KUBE_CONFIG` is set.
- **DoD verified**: temporarily disabled the duplicate-ISBN check → `BookServiceImplTest.createBook_duplicateIsbn_throwsDuplicate` went red (`mvn` BUILD FAILURE, i.e. the pipeline would be red); reverted → green, no markers left.
- **Monitoring**: Micrometer + `/actuator/prometheus` exposed on all services (dependency added to common + config-server + gateway; exposure centralized in config-repo). `docs/MONITORING.md` defines per-service metrics (QPS, error rate, p99, HikariCP pool, Resilience4j circuit-breaker state, Kafka consumer lag) and alert thresholds; verified config-server + gateway still boot with the Prometheus registry.
- **First real CI run went red** (`UserRepositoryTest` duplicate-username) even though `mvn clean verify` was green locally — a cross-class data leak through the singleton Postgres container (committing `SecurityIntegrationTest` only cleaned `@BeforeEach`). Fixed with `@AfterEach` cleanup so no committed row outlives the class; recorded in `BUGLOG.md`. This is also the DoD "pipeline goes red on a real failure → fix → green" evidence.
- Next step: Phase 12 (deliverables — architecture diagram, AI-generated frontend, demo video, improvement notes), **after manual confirmation**

## 2026-07-27 — Phase 12: Deliverables

- **Architecture diagram** (`docs/03-ARCHITECTURE.md`): full-picture Mermaid diagrams — system overview (SPA → gateway → services → DB-per-service + Kafka + AWS), the order→payment sequence with idempotent consumers, and the CI/CD → GHCR → Kubernetes delivery view; plus an invariants table.
- **Frontend** (`frontend/index.html` + README): build-free single-file React SPA (React + Babel via CDN, JSX in-browser) that exercises the documented API through the gateway — register/login (JWT in `localStorage` → Bearer), catalog, view (records browsing history), cart → place order, pay/cancel, recently-viewed. CORS already open on the gateway.
- **Improvement notes** (`docs/IMPROVEMENTS.md`): honest limitations + next steps across security, data/transactions (saga+outbox), resilience, testing, delivery, frontend; top-3 prioritized.
- **Demo script** (`docs/DEMO-SCRIPT.md`): 8–12 min recordable walkthrough for the demo video (the video recording itself is the one manual, non-committable deliverable).
- **Evolution check**: 12 tags `phase-0 … phase-11`, small typed commits (`feat|fix|test|docs|…`) — the history reads as the monolith→microservices story.
- Boundary: nothing new in the backend; Phase 12 is documentation + demo client only.
