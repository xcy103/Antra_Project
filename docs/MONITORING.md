# Monitoring — Phase 11

Every service exposes Micrometer metrics at **`/actuator/prometheus`** (plus `/actuator/health` for
probes). A Prometheus server scrapes each pod; Grafana dashboards + Alertmanager rules sit on top.
This doc defines the metrics that matter per service and the alerts on them.

## The four golden signals (all services)

From the built-in `http_server_requests_seconds` timer (tags: `uri`, `method`, `status`, `outcome`):

| Signal | Metric / query |
|---|---|
| **Throughput (QPS)** | `rate(http_server_requests_seconds_count[1m])` |
| **Error rate** | `rate(http_server_requests_seconds_count{status=~"5.."}[5m]) / rate(http_server_requests_seconds_count[5m])` |
| **Latency p99** | `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))` |
| **Saturation** | JVM: `jvm_memory_used_bytes` / `jvm_gc_pause_seconds`, `process_cpu_usage`; plus per-service resources below |

## Per-service specifics

- **api-gateway** — edge QPS + p99 (all client traffic), and the **401 rate** at the edge
  (`http_server_requests_seconds_count{status="401"}`) to spot auth problems / abuse.
- **user / book / order / payment** (JPA) — **DB connection pool** via HikariCP:
  `hikaricp_connections_active`, `_idle`, `_pending`, `hikaricp_connections_timeout_total`. Pending > 0
  or timeouts rising ⇒ pool exhaustion.
- **order-service** — **circuit-breaker state** for the book-service call:
  `resilience4j_circuitbreaker_state` and `resilience4j_circuitbreaker_calls` (Resilience4j → Micrometer).
- **order / payment** (producers) — `kafka_producer_record_send_total`, `kafka_producer_record_error_total`.
- **notification / analytics** (consumers) — **consumer lag**:
  `kafka_consumer_fetch_manager_records_lag` per topic/partition (how far behind real-time each group is).

## Alert rules (starting thresholds)

| Alert | Condition | Severity |
|---|---|---|
| Service down | health != UP / no scrape for 1m | page |
| High error rate | 5xx ratio > 5% for 5m | page |
| High latency | p99 > 1s for 5m | warn |
| DB pool exhausted | `hikaricp_connections_pending > 0` for 2m, or timeout_total increasing | warn |
| Circuit breaker open | `resilience4j_circuitbreaker_state{state="open"} == 1` (order → book) | warn |
| Kafka consumer lag | `kafka_consumer_fetch_manager_records_lag > 1000` for 5m | warn |
| JVM memory pressure | heap used / max > 0.9 for 10m, or frequent full GC | warn |

## Health vs metrics

`/actuator/health` drives Kubernetes liveness/readiness (restart / route decisions); it is **not** a
substitute for metrics — a pod can be "UP" while its error rate or latency is unacceptable, which is
what the alerts above catch. In production these alerts page via Alertmanager; the DoD verification for
this phase is the **CI pipeline going red on a failing test** (see `.github/workflows/ci.yml`).
