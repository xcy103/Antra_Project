# ROADMAP — Phase Plan

> Rule: **do one Phase at a time.** Each Phase ends with a mandatory manual acceptance; only after it passes do we start the next.
> Current progress is tracked in `PROGRESS.md`.

## Progress overview

| Phase | Doc step | Status |
|---|---|---|
| 0 | Environment & repo bootstrap | ✅ `phase-0` |
| 1 | Step 1 Monolith skeleton + AOP | ✅ `phase-1` |
| 2 | Step 2 Data layer | ✅ `phase-2` |
| 3 | Step 3 Auth & security | ✅ `phase-3` |
| 4 | Step 4 Testing suite | ✅ `phase-4` |
| 5 | Step 5 Split into microservices | ✅ `phase-5` |
| 6 | Step 6 Config center | ✅ `phase-6` |
| 7 | Step 7 Kafka | ✅ `phase-7` |
| 8 | Step 8 API Gateway | ✅ `phase-8` |
| 9 | Step 9 AWS S3/Lambda/DynamoDB | ✅ `phase-9` |
| 10 | Step 10 Containerization & K8s | ✅ `phase-10` |
| 11 | Step 11 CI/CD & monitoring | ✅ `phase-11` |
| 12 | Deliverables (video/architecture diagram/improvement notes/frontend) | 🚧 In progress |

---

## Phase 0 — Environment & repo bootstrap

**Do**: `git init` + `.gitignore`; confirm JDK 17 / Maven / Docker are available; a `docker-compose.yml` that brings up only a single PostgreSQL; an empty Spring Boot project that starts with `mvn spring-boot:run` and returns 200.

**Don't**: any business code, any entity, any database table.

**DoD**: `mvn clean verify` passes; `docker compose up -d postgres` comes up; `curl localhost:8080/actuator/health` returns UP.

---

## Phase 1 — Monolith skeleton (Step 1)

**Do**
- Directory structure: `controller / service / repository / entity / dto / exception / aop`
- `Book` entity + `BookRepository` + `BookService`(interface) + `BookServiceImpl`
- 5 CRUD endpoints (all PUBLIC at this phase): `GET /api/books`, `GET /api/books/{id}`, `POST`, `PUT`, `DELETE`
- `BookRequestDto` (with `@NotBlank`/`@Positive` etc. validation), `BookResponseDto`
- `GlobalExceptionHandler` + `ResourceNotFoundException` + `ErrorResponse`, mapping 400/404/409
- `LoggingAspect`: `@Around` on the service layer, logging method name, args, elapsed time
- `application.yml` + `application-dev.yml` + `application-prod.yml` — three profiles

**Don't**
- ❌ No Spring Security / JWT (Phase 3)
- ❌ No Author entity, no Flyway (Phase 2)
- ❌ No Kafka / Feign / Gateway dependencies
- ❌ No Dockerfile

**Test requirement**: `BookServiceImplTest` (Mockito mock repository), covering create success, not-found throws `ResourceNotFoundException`, invalid args.

**DoD**: app starts; all 5 endpoints work via curl/Postman; layering is clean (controller has no repository dependency); exceptions return structured JSON; AOP-logged method timings are visible in the logs.

---

## Phase 2 — Data layer (Step 2)

**Do**
- Switch to Docker PostgreSQL, `ddl-auto: validate`
- `Author` entity, `Book` gets `@ManyToOne → Author`
- Flyway `V1__init.sql`: create author / book tables, including PK, FK, `isbn` unique, `stock >= 0` CHECK
- Index on `book.title` or `book.author_id`, **and explain why in `02-DESIGN.md`**
- Multi-step writes get `@Transactional`
- Deliberately create an N+1 (list authors and their books), turn on SQL logging to confirm it, then fix it with `JOIN FETCH` / EntityGraph, **recording the before/after SQL counts in the design doc**
- Run `EXPLAIN ANALYZE` on the heaviest query, paste the plan into the design doc, confirm the index is used
- Add `@Version` optimistic locking to `Book.stock`

**Don't**
- ❌ Don't touch security / users
- ❌ Don't create order / payment tables (those get their own services in Phase 5)
- ❌ Don't fall back to `ddl-auto: update` just to "make it run"

**Test requirement**: `BookRepositoryTest` (`@DataJpaTest` + Testcontainers PostgreSQL); a concurrent stock-update test proving optimistic locking works (throws `OptimisticLockException`).

**DoD**: data really lands in PostgreSQL; indexes have a rationale; transactions protect multi-step writes; can show the EXPLAIN plan and the before/after SQL comparison for the N+1 fix.

---

## Phase 3 — Auth & security (Step 3)

**Do**
- `User` entity (username / email / passwordHash / role) + `UserRepository.findByUsername`
- `AuthController`: `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`
- BCrypt password encryption (`PasswordEncoderConfig`)
- `JwtUtil` (issue + validate, incl. expiry), `JwtAuthenticationFilter`, `CustomUserDetailsService`
- `SecurityConfig`: stateless filter chain; catalog reads PUBLIC, writes ADMIN
- Flyway `V2__users.sql`

**Don't**
- ❌ OAuth2 Google login (optional; goes to BACKLOG, revisit after the main line is done)
- ❌ Don't hardcode the JWT secret into yml

**Test requirement**: accessing a protected endpoint with no token returns 401; a USER hitting an ADMIN endpoint returns 403; an expired token is rejected; the password in the DB is a hash, not plaintext.

**DoD**: register/login works; token expiry takes effect; can explain how the server validates a token (signature, expiry, claims).

---

## Phase 4 — Testing suite (Step 4)

**Do**
- Fill out all four layers of tests: `service` (Mockito), `controller` (`@WebMvcTest`), `repository` (`@DataJpaTest`), `integration` (`@SpringBootTest` + Testcontainers)
- One end-to-end integration test: register → login → create book (ADMIN) → query book
- Security tests included in the suite
- Generate a coverage report (JaCoCo)

**Don't**
- ❌ Don't write assertion-less tests just to pad coverage
- ❌ Don't change the implementation to accommodate tests

**DoD**: `mvn clean verify` runs everything green in one command; deliberately breaking one piece of business logic must turn a test red (do this verification once and record it).

---

## Phase 5 — Split into microservices (Step 5)

**Do**: split into `user-service` / `book-service` / `order-service` / `payment-service`, each with its own PostgreSQL database; order→book via OpenFeign (explicit timeouts) + Resilience4j circuit breaker/retry/fallback; JWT propagation; order/payment tables created.

**Don't**: ❌ No Kafka this phase (Phase 7), no Gateway (Phase 8), no Config Server (Phase 6); services don't share a database and don't create cross-DB foreign keys.

**Acceptance demo**: **kill book-service, and a place-order request must degrade gracefully instead of cascading into timeouts.** This is the core acceptance action of this phase.

**Design deliverable**: write the Saga / eventual-consistency plan into `02-DESIGN.md` (the compensation path for order + payment + stock).

---

## Phase 6 — Config center (Step 6)

**Do**: `config-server` (`@EnableConfigServer`) + `config-repo` (one yml per service + a shared application.yml); services pull config via `spring.config.import`; secrets via environment variables.
**Don't**: ❌ Don't put plaintext passwords into config-repo.
**Design deliverable**: write a paragraph on "how this is replaced by ConfigMap/Secret in a real K8s deployment" — the doc explicitly says the interview will ask.

---

## Phase 7 — Kafka (Step 7)

**Do**: bring up Kafka in Docker; order publishes `OrderPlaced`, payment publishes `PaymentCompleted`; new `notification-service` and `analytics-service`, two **different consumer groups** consuming the same topic; messages keyed by orderId for ordering; idempotent consumers; DLQ (challenge).
**Don't**: ❌ These two services expose no business REST API, only `/actuator/health`.
**Test requirement**: redeliver the same message and assert it isn't processed twice (idempotency test).

---

## Phase 8 — API Gateway (Step 8)

**Do**: Spring Cloud Gateway routes the four services by path; edge-validates JWT and propagates identity; CORS is configured here.
**Don't**: ❌ No business logic in the Gateway.
**DoD**: all client traffic goes through a single address; unauthenticated requests are rejected at the edge.

---

## Phase 9 — AWS (Step 9)

**Feature A cover**: upload to S3 → Lambda (`CoverImageHandler`) → write DynamoDB `CoverMetadata` → SNS/SES email. Use bookId as a deterministic key + conditional write so duplicate events don't produce duplicate records or duplicate emails.
**Feature B browsing history**: on a logged-in user's `GET /api/books/{id}`, **asynchronously** write DynamoDB `UserBrowsingHistory` (PK=userId, SK=viewedAt), expose `GET /api/books/me/history` in reverse chronological order; set TTL 30 days.
**Fallback**: with no AWS account, write a full flow design doc + local LocalStack simulation (explicitly allowed by the doc). **This decision must be asked first.**

---

## Phase 10 — Containerization & orchestration (Step 10)

**Do**: multi-stage Dockerfile per service (JDK build → JRE run) + `.dockerignore`; `docker-compose.yml` brings up the whole stack (all services + their PGs + Kafka); under `k8s/`, per-service Deployment+Service, ConfigMap, Secret, liveness/readiness probes wired to Actuator; HPA (challenge).
**DoD**: `docker compose up` brings up a working full stack.

---

## Phase 11 — CI/CD & monitoring (Step 11)

**Do**: `.github/workflows/ci.yml` — on push/PR: build → test → build image → tag by commit SHA and push → (deploy, or a design with a manual-approval deploy stage); a failing test must make the pipeline red. Each service exposes Actuator health endpoints. Define and write down per-service monitoring metrics (QPS, error rate, p99, Kafka consumer lag, DB connections) and alert items.
**DoD**: deliberately commit a failing test; the pipeline must go red (do this verification once and keep a screenshot as evidence).

---

## Phase 12 — Deliverables

- [x] Architecture diagram (full picture incl. frontend/backend/database/AWS/K8s) — `docs/03-ARCHITECTURE.md`
- [x] AI-generated React frontend calling the documented API — `frontend/index.html`
- [ ] Demo video — script ready (`docs/DEMO-SCRIPT.md`); recording is a manual step
- [x] "What still needs improvement" write-up — `docs/IMPROVEMENTS.md`
- [x] Check that the git log clearly reflects the step-by-step evolution — 12 tags `phase-0 … phase-11`, small typed commits
