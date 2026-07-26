# CLAUDE.md — Development Rules and Boundaries

> This file constrains how the AI agent behaves in this repo. **Before starting any work, read this and `docs/01-ROADMAP.md` first, confirm the current phase, and do only what belongs to that phase.**

## Language

**Everything committed to this repo is in English** — docs, filenames, code, comments, commit messages. The only exception is verbal progress reports back to the user, which may be in Chinese. No Chinese in any repo file.

## Project

Bookstore microservices capstone. Source requirement doc: `docs/capstone-project.docx`. Annotated walkthrough: `docs/00-project-overview.md`.

## Tech Stack (fixed — do not swap without asking)

- Java 17, Maven, Spring Boot 3.x
- PostgreSQL (run in Docker), Flyway for schema management
- JUnit 5 + Mockito + Testcontainers
- Later phases: OpenFeign, Resilience4j, Spring Cloud Config, Spring Cloud Gateway, Kafka, AWS (S3/Lambda/DynamoDB/SNS), Docker, Kubernetes, GitHub Actions

## Hard Rules

### 1. Phase boundaries
- **Implement only the current Phase.** Do not pull in dependencies, annotations, directories, or config from the next phase early.
  - e.g. Phase 1 must contain zero Spring Security, Kafka, or Feign code or pom dependencies.
- If, while doing the current task, you're tempted to "quickly add" something from a later phase → **stop, record it in `docs/BACKLOG.md`, and write no code.**
- After each Phase is done, stop and wait for manual sign-off. Do not auto-advance to the next Phase.

### 2. Tests are part of "done", not an afterthought
- **A feature without tests is not done.** "Implement now, add tests later" is not allowed.
- Tests must verify **real behavior** — no empty assertions, no asserting `true`, no mocking out the class under test, no editing/deleting tests to accommodate the implementation just to make them green.
- Before delivering each Phase, one command must run everything green: `mvn clean verify`.
- Tests must cover the **happy path + at least one failure/edge path** (e.g. insufficient stock, resource not found, validation failure, no permission).
- When fixing a bug: **first write a failing test that reproduces the bug, then change the code to make it pass.**

### 2b. Build verification runs on the local machine (important)
- The agent's sandbox **has no Maven, and Maven Central is blocked by network policy**, so it cannot download dependencies — meaning **the agent cannot run `mvn clean verify`**.
- So the workflow is: **agent writes implementation and tests → the user runs the build in IntelliJ or the terminal → pastes the failure output back → agent fixes it.**
- The agent **must not** claim "it should pass" just because it can't run the build itself. Not run = not verified; it must explicitly say "pending local verification".
- Each Phase's acceptance is judged by the **real output of `mvn clean verify` on the local machine.**

### 3. No fake implementations
- No shipping `// TODO: implement` as if done; no service methods that return hardcoded fake data.
- Don't declare done just because "it looks like it runs" — each Phase's Definition of Done must be checked off item by item.
- For anything you can't finish, say clearly "not done / can't do it", write it into `docs/BACKLOG.md`, and don't silently skip it.

### 4. Commit discipline (this is a grading item)
- Small commits, one logical change per commit, so the Phase-by-Phase evolution is visible.
- Format: `feat(book): add Book CRUD endpoints` / `test(book): add BookServiceImpl unit tests` / `fix(order): prevent negative stock`
- **Do not** squash a whole Phase's changes into one big commit.
- Do not commit `.env`, secrets, `target/`, or IDE config.

### 5. Layering and dependency direction
```
controller → service(interface) → serviceImpl → repository → entity
                    ↑
                   dto (in/out params; never expose entities directly to the controller)
```
- Controllers **must not** inject repositories directly.
- Entities **must not** appear in controller method signatures — always go through DTOs.
- Dependency injection is always **constructor injection**, never `@Autowired` field injection.
- Business exceptions throw custom exceptions, mapped to HTTP status codes centrally by a `@RestControllerAdvice`.

### 6. Database
- Schema changes **only** through Flyway migration scripts (`V{n}__desc.sql`); do not rely on `ddl-auto: update` (use `validate` even in local dev).
- Already-committed migration scripts **must not be modified**, only added to.
- **No foreign keys across services** (Database per Service). `orders.user_id` and `order_item.book_id` are bare ids.
- Multi-step writes must be `@Transactional`.

### 7. Security
- Passwords stored only as BCrypt hashes; no plaintext password anywhere.
- Secrets/JWT secret/DB password go through environment variables or the config server — **never hardcoded into yml committed to Git**.
- No logging of tokens, passwords, or full personal information.
- **This is a public repo** — before any commit, confirm no credentials are smuggled in.
- AWS credentials only via the local `~/.aws/credentials` or environment variables; code always uses the SDK default credential chain — **never any access key / secret key literal**.
- Where a placeholder is needed, write `${ENV_VAR}` and list the variable name (not its value) in `.env.example`.

### 8. Git workflow
- Default branch `main`. The remote is a **public GitHub repo**.
- The agent **only makes local commits, does not push**. Pushing is done by the user in IntelliJ or the command line.
- When to commit: **every time a self-describable feature/fix/test is complete, commit once** — don't pile up until the end of a Phase.
- At the end of each Phase, additionally tag it: `phase-1`, `phase-2`, … for traceability of the evolution.
- Commit messages in English, following `type(scope): summary`, type from `feat|fix|test|refactor|docs|chore|build|ci`.

## When to ask me first (don't decide on your own)

- Deviating from the tech stack or versions pinned in this doc
- Changing already-decided API paths, role permissions, or database table structure
- Any item in a Phase's Definition of Done you can't achieve
- Adding a module or third-party dependency not previously planned
- Anything requiring a real AWS account / that costs money

## Per-Phase delivery checklist

Before declaring a Phase complete, the developer/agent self-checks each item:

- [ ] `mvn clean verify` all green
- [ ] Every item in this Phase's Definition of Done can be demonstrated
- [ ] New code has matching tests, including at least one failure path
- [ ] No dependencies from the next phase introduced
- [ ] Commits are reasonably split, with clear messages
- [ ] `docs/PROGRESS.md` updated (what was done, what's left, next step)
- [ ] Technically valuable problems from this phase recorded in `docs/BUGLOG.md` (STAR format) — substantive, explainable bugs only; skip trivial config typos and dependency-version mismatches
- [ ] Can verbally explain the technical choices for this step (interview-oriented)

## Directory conventions

```
Antra_Project/
├── CLAUDE.md                    # this file
├── docs/
│   ├── capstone-project.docx    # original requirement
│   ├── 00-project-overview.md   # annotated requirement walkthrough
│   ├── 01-ROADMAP.md            # phase plan (current progress lives here)
│   ├── 02-DESIGN.md             # architecture and design decisions
│   ├── PROGRESS.md              # progress log
│   └── BACKLOG.md               # deferred / unfinished items
└── bookstore/                   # Phase 1-4 monolith; split into bookstore-platform/ after Phase 5
```
