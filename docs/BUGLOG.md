# BUGLOG — notable bugs and how they were solved

Recorded in the STAR format (Situation / Task / Action / Result). Newest first.
Interview-oriented: each entry should be explainable end-to-end.

**What goes here:** technically valuable problems that needed real analysis and have
explainable depth. **What to skip:** trivial config typos and dependency-version
mismatches — those aren't worth an entry.

---

## 2026-07-26 — Colima can't bind-mount its docker socket into Ryuk (Phase 5/6)

**Situation.** After the platform grew to several Testcontainers-using modules, `mvn clean verify`
failed at container startup for every integration test:
`Ryuk … Status 500: error while creating mount source path
'/Users/…/.colima/default/docker.sock': … operation not supported`. All non-Docker tests passed.

**Task.** Get Testcontainers working again across all service modules without a code change (the app
code was correct — only the test infra was failing).

**Action.** Testcontainers starts **Ryuk**, a small reaper container, and **bind-mounts the Docker
socket into it** so Ryuk can clean up leftover containers. On Colima the daemon runs in a Linux VM and
the host socket path `~/.colima/default/docker.sock` is not a mountable source inside that VM
("operation not supported") — so Ryuk can't start, and every Testcontainers test aborts. (This is
Colima-specific; on Docker Desktop / CI Linux the socket mounts fine.) Disabled Ryuk with
`TESTCONTAINERS_RYUK_DISABLED=true`; Testcontainers then falls back to a JVM shutdown hook to stop
containers — fine for local dev.

**Result.** `TESTCONTAINERS_RYUK_DISABLED=true mvn clean verify` → **BUILD SUCCESS, all 7 modules**.
This is a **local-machine** setting (not committed): on CI/Linux Ryuk works normally. Documented in
`README.md`. Lesson: Ryuk needs to bind-mount the docker socket; with a VM-based engine whose socket
isn't a shareable host path, disable Ryuk and let the JVM shutdown hook do the cleanup.

---

## 2026-07-25 — `@WebMvcTest(addFilters=false)` still creates filter beans (Phase 4)

**Situation.** The new `@WebMvcTest` controller tests all failed to even load the context:
`UnsatisfiedDependencyException: Error creating bean 'jwtAuthenticationFilter' ... No qualifying bean
of type 'com.bookstore.security.JwtUtil'`.

**Task.** Get the web-slice tests to load, while keeping them focused on controller logic (mapping,
validation, error status) rather than security.

**Action.** `@WebMvcTest` narrows the context, but it *does* include servlet `Filter` beans — and
`JwtAuthenticationFilter` is one. `@AutoConfigureMockMvc(addFilters = false)` only stops filters from
being *applied* to the MockMvc chain; the filter bean is still *instantiated*, so its `JwtUtil`
constructor dependency must exist. `JwtUtil` is a plain `@Component`, which the slice does not create.

**Result.** Added `@MockitoBean JwtUtil` to each controller test to satisfy the filter bean; the
slice loads and the tests pass. Lesson: `addFilters=false` disables filter *execution*, not filter
*bean creation* — a component picked up by the slice still needs its dependencies present.

---

## 2026-07-25 — Shared Testcontainers container + Spring context caching → 30s health-check hang (Phase 3)

**Situation.** After Phase 3 added a 4th test class using the Testcontainers base, `mvn clean verify`
failed on one assertion: `BookstoreApplicationTests.healthEndpointReportsUp`. The log showed the DB
health contributor took **30017ms** (Hikari's default connection timeout) and `/actuator/health`
returned non-2xx. `contextLoads` in the same class passed; only the health test failed.

**Task.** Make the full suite green without weakening tests, and understand why adding one class
broke a previously-passing test.

**Action.** The base class started PostgreSQL via a `static` `@Container` field and was inherited by
several test classes. `@Container` manages a **per-class** lifecycle: it stops the container in each
class's `afterAll`. But the container was a single shared static instance, and Spring **caches and
reuses** application contexts across `@SpringBootTest` classes with identical config. So one class's
`afterAll` stopped the shared container while a reused context still pointed at it — `contextLoads`
passed (cached context, no DB call), but the health endpoint's real `SELECT` hit the dead container
and blocked until Hikari's 30s timeout. The tell was that it only broke once enough classes ran to
force the stop-then-reuse ordering.

**Result.** Switched to the Testcontainers **singleton-container** pattern: start the container once
per JVM in a `static {}` initializer, never stop it per-class (Ryuk reaps it at JVM exit), and drop
`@Testcontainers`/`@Container`. One always-running container backs every cached context. Suite green
(32 tests). Lesson: a shared static container and Spring's context cache must agree on lifecycle —
`@Container`'s per-class stop/start does not.

---

## 2026-07-25 — `GenerationType.IDENTITY` makes constraint violations surface at `save()`, not `flush()` (Phase 2)

**Situation.** The repository tests for the unique-ISBN and `stock >= 0` constraints asserted the
`DataIntegrityViolationException` on a later `entityManager.flush()`, but the exception was thrown
earlier and the tests failed.

**Task.** Assert the DB constraint violations at the point they actually occur.

**Action.** With `GenerationType.IDENTITY`, Hibernate cannot batch inserts — it must execute the
`INSERT` immediately on `save()`/`persist()` to obtain the DB-generated id. So a unique/check
violation fires during `save()`, not at a deferred `flush()`. (This is a real semantic difference
from `SEQUENCE`/`TABLE` generators, where inserts can be deferred until flush.)

**Result.** Asserted the violation on `saveAndFlush(...)` (the write that triggers the immediate
INSERT). Tests correctly verify the DB constraints. Lesson: id-generation strategy changes *when*
writes hit the database, which changes where exceptions surface in a test.

---

## 2026-07-25 — Testcontainers can't reach Docker (`mvn clean verify` fails)

**Situation.** Phase 2 added Testcontainers-based integration tests (`@DataJpaTest` +
`@SpringBootTest` against a real PostgreSQL). On macOS with Docker Desktop 4.83, `mvn clean verify`
failed: the 9 unit tests passed, but all 3 container-based tests errored before running with:

```
Could not find a valid Docker environment.
  ...Strategy: failed with exception BadRequestException (Status 400: {"ID":"","Containers":0,...,
     "Labels":["com.docker.desktop.address=unix:///.../docker-cli.sock"], ...})
```

The `docker` CLI, `docker compose`, and `curl --unix-socket .../docker.sock /info` all worked (HTTP
200) — only Testcontainers' Java client (docker-java) got the 400.

**Task.** Make the Testcontainers tests actually run so `mvn clean verify` is fully green — without
weakening the tests — and understand the root cause well enough to survive later Testcontainers
phases (4, 7).

**Action.**
1. Confirmed the daemon was healthy for the CLI/curl but not docker-java. The 400 body carried the
   label `com.docker.desktop.address=...docker-cli.sock`, pointing at **Docker Desktop 4.83's
   API-proxy** as the thing returning 400 to docker-java.
2. Tried the usual socket fixes — `DOCKER_HOST` overrides, `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`,
   the raw engine socket `docker.raw.sock`, and a Testcontainers version bump (1.20.6). All still
   returned the same 400 via Docker Desktop.
3. Pointed Testcontainers at **Colima** instead (a plain dockerd, no API-proxy) via
   `~/.testcontainers.properties` → `docker.host=unix:///.../.colima/default/docker.sock`. This got
   past the proxy, and the *real* engine error finally surfaced:
   ```
   client version 1.32 is too old. Minimum supported API version is 1.40,
   please upgrade your client to a newer version
   ```
4. **Root cause:** Testcontainers 1.20.4's bundled docker-java negotiates Docker API **1.32** by
   default, but both local engines (Docker Desktop 4.83 and Colima's Docker) require **≥ 1.40**.
   Docker Desktop's API-proxy had simply masked this with a generic 400 instead of the real message.
5. Pinned a modern API version. docker-java reads the **`api.version` system property** (not the
   `DOCKER_API_VERSION` env var, which is why setting the env var on the `mvn` line did nothing —
   and Surefire forks a separate test JVM anyway). Set it in the Surefire config so it reaches the
   forked JVM (`systemPropertyVariables/api.version=1.41`, with the env var as a fallback).
6. Running the tests then uncovered two *real* test bugs (good — the tests were finally executing):
   - Constraint tests asserted the violation on a later `flush()`, but `GenerationType.IDENTITY`
     makes `save()` INSERT immediately, so the violation fires on `saveAndFlush()` — fixed the
     assertion target.
   - The N+1 test wasn't isolated from the `V2` seed authors (3 of them), skewing the query counts
     (naive 7 vs expected 4; fetch-join returned 6 authors vs 3) — fixed by clearing the tables at
     the start of the test transaction (rolled back afterwards).

**Result.** `mvn clean verify` → **BUILD SUCCESS, 18 tests**. Working local setup:
- **Colima** is the Docker runtime for tests: `colima start` (Docker Desktop's API-proxy is
  incompatible with docker-java on this machine).
- `~/.testcontainers.properties` → `docker.host` = Colima's socket (machine-local, not committed).
- `api.version=1.41` pinned in the build (committed in `pom.xml`, portable — every modern Docker
  supports 1.41).

Note: the AI agent's own sandbox **cannot** run Testcontainers (it force-routes docker-java to
Docker Desktop and ignores `DOCKER_HOST`/`~/.testcontainers.properties`), so this was diagnosed
partly by reasoning and verified on the local machine. See `README.md` → Local Development for the
run recipe.
