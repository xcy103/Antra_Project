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
