# BUGLOG — notable bugs and how they were solved

Recorded in the STAR format (Situation / Task / Action / Result). Newest first.
Interview-oriented: each entry should be explainable end-to-end.

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
