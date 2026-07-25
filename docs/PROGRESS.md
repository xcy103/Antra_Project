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
