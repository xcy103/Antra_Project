# What Still Needs Improvement

An honest, interview-oriented list of the platform's current limitations and what I'd do next. The
capstone deliberately optimizes for breadth (touching each concept end to end) over production
hardening, so several deliberate shortcuts are called out here.

## Security

- **Symmetric JWT with a shared secret.** Every service and the gateway share one HMAC secret. That
  means any service could *mint* tokens, not just verify them. **Next:** asymmetric signing (the
  user-service holds the private key; others verify with the public key / JWKS), plus short-lived
  access tokens and refresh tokens.
- **No token revocation.** A stolen/again-used JWT is valid until it expires. **Next:** a denylist
  (Redis) checked at the edge, or drop to opaque tokens with gateway introspection.
- **CORS is wide open** (`allowedOrigins: "*"`) for local dev. **Next:** pin origins per environment.
- **Gateway trusts forwarded identity.** Services accept the user the gateway passes down; nothing
  stops a request that reaches a service directly (inside the cluster) from spoofing it. **Next:**
  mTLS between gateway and services, or re-verify the token at each service.

## Data & transactions

- **No distributed transaction / saga.** Order → payment is two independent local transactions. If
  payment never happens, the order sits `PENDING` forever with stock effectively reserved. **Next:** a
  saga (orchestrated or choreographed) with compensating actions, plus an order-expiry/timeout job.
- **Stock check is read-then-write across services.** `order-service` reads stock from `book-service`
  then persists; two concurrent orders can both pass the check. Optimistic locking protects a single
  book row, but not the cross-service reserve. **Next:** a real reservation step (reserve → confirm →
  release) owned by book-service.
- **No outbox pattern.** Events are published in the same method as the DB write; a crash between
  commit and publish can lose an event. **Next:** transactional outbox + a relay (or Debezium CDC).

## Resilience & performance

- **No retries/timeouts tuning beyond defaults** on the Feign calls, and no bulkhead isolation.
  **Next:** per-dependency timeouts, retry with backoff on idempotent reads, bulkheads.
- **No caching.** The catalog is read on every page load straight from Postgres. **Next:** cache
  `GET /api/books` (Redis / HTTP caching) with invalidation on catalog writes.
- **N+1 was fixed with JOIN FETCH**, but there's no pagination on `GET /api/books` — it returns the
  whole catalog. **Next:** pageable endpoints.

## Testing & quality

- **Ordering-sensitive integration tests.** The singleton Postgres container is shared across test
  classes; committing tests must clean up `@AfterEach` or they leak (this exact bug went green locally
  / red on CI — see `BUGLOG.md`). **Next:** enforce isolation (per-class schema, `@Transactional`
  where possible, or Testcontainers-per-class where the cost is acceptable), and randomize test order
  in CI to surface coupling early.
- **No contract tests** between services (e.g. order↔book). **Next:** consumer-driven contracts
  (Spring Cloud Contract / Pact) so a book-service change can't silently break order-service.
- **Coverage is measured (JaCoCo) but not gated.** **Next:** a coverage floor + mutation testing
  (PIT) on the core services.

## Delivery & operations

- **Deploy stage is a stub.** It runs `kubectl set image` only if a `KUBE_CONFIG` secret exists; there
  is no real cluster wired up, no health-gated rollout, no rollback. **Next:** a real environment
  (EKS), rolling deploys with automatic rollback on failed readiness, and progressive delivery
  (canary / blue-green).
- **No centralized logging or tracing.** Metrics exist (Prometheus) but there's no distributed
  tracing across the gateway→service→Kafka hops. **Next:** OpenTelemetry traces + a log aggregator
  (correlation id propagated from the edge).
- **Secrets are env/ConfigMap-level.** **Next:** a real secrets manager (AWS Secrets Manager / Vault)
  and sealed secrets in Git.
- **Config-server serves `native` classpath files.** **Next:** a Git-backed config repo with
  per-environment profiles and refresh.

## Frontend

- **Demo-grade only:** no build pipeline, CDN React, JWT in `localStorage`, no automated tests, no
  error boundaries beyond toasts. It exists to make the API tangible, not to ship. **Next:** a real
  Vite/Next build, token handling in memory + httpOnly refresh cookie, and component tests.

## Prioritized next three

1. **Saga + outbox for order→payment** — the biggest correctness gap.
2. **Asymmetric JWT + gateway-only trust boundary (mTLS)** — the biggest security gap.
3. **Real gated deploy to EKS with rollback + tracing** — the biggest operability gap.
