# BACKLOG — Deferred / Unfinished Items

> Record "would be nice to do" ideas that come up during development here, instead of writing code for them on the spot.

| Recorded | From Phase | Item | Plan |
|---|---|---|---|
| 2026-07-24 | Phase 3 | OAuth2 Google login (marked optional in the doc) | Re-evaluate after all 11 main-line steps are done |
| 2026-07-24 | Phase 5 | Full Saga / distributed-transaction implementation (marked challenge) | Phase 5 ships a design doc first; implement as time allows |
| 2026-07-24 | Phase 7 | Kafka DLQ + deep DLQ monitoring (challenge) | Do after the Phase 7 main flow passes |
| 2026-07-24 | Phase 9 | Lambda DLQ, S3 lifecycle cost optimization (challenge) | Do after the Phase 9 main flow passes |
| 2026-07-24 | Phase 10 | HPA, EKS + IRSA design notes (challenge) | Do after the Phase 10 main flow passes |
| 2026-07-24 | Phase 11 | Blue-green/canary releases + rollback, Prometheus/Grafana dashboards (challenge) | Do after the Phase 11 main flow passes |
| 2026-07-24 | Phase 3 | ~~`LoggingAspect` logs all service args; must mask sensitive ones (passwords, tokens)~~ | ✅ Resolved in Phase 3: `RegisterRequest`/`LoginRequest` override `toString()` to redact the password; the aspect logs args only (not return values), so JWTs aren't logged either |

> Confirmed: the user has a usable AWS account. Phase 9 goes with **real deployment**, not the LocalStack fallback.
