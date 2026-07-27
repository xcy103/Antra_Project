# Demo Script

A tight, recordable walkthrough of the platform for the capstone demo video. Target length **8–12
minutes**. Each section lists what to show and the one line to say. Adjust ports if you run services
individually instead of via compose.

## 0. Before you record

- [ ] Colima running (`colima start`) — needed for Testcontainers if you show the build.
- [ ] `cd bookstore-platform && docker compose up -d` → wait for all services healthy
      (`docker compose ps`).
- [ ] Open `frontend/index.html` in a browser; gateway URL shows `http://localhost:8080`.
- [ ] Have the GitHub repo's **Actions** tab open in a second tab.
- [ ] (Optional) A REST client (curl / Postman) for the raw API moments.

## 1. The story (30s)

> "This is a bookstore that starts as a single Spring Boot monolith and evolves, phase by phase, into
> a containerized microservices platform on AWS. The commit history and the `phase-0 … phase-11` tags
> are the story of that evolution."

Show: `git log --oneline` and `git tag` briefly.

## 2. Architecture (60s)

Show: [`docs/03-ARCHITECTURE.md`](03-ARCHITECTURE.md) rendered on GitHub (the diagrams).

> "The SPA only talks to the gateway. The gateway verifies the JWT and routes to four services, each
> with its own database. Orders call books synchronously through a circuit breaker; orders and
> payments publish Kafka events that notification and analytics consume independently. Covers are an
> async S3 → Lambda → DynamoDB pipeline."

## 3. Auth (60s)

In the web client:
1. **Register** a user → show the toast; note the JWT lands in `localStorage`.
2. **Log out**, **log in** again.

> "Passwords are stored only as BCrypt hashes. Login returns a JWT; every later call carries it as a
> Bearer token, and the gateway is what actually verifies it."

Optional raw call:
```bash
curl -s localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password123"}'
```

## 4. Catalog + browsing history (60s)

1. Browse the catalog (`GET /api/books`).
2. Click **View** on a couple of books → point out **Recently viewed** filling in.

> "Viewing a book records a browsing-history event in DynamoDB — the read path that shows off the AWS
> integration."

## 5. Place an order → circuit breaker (90s)

1. Add books to the cart, **Place order** → show the new `PENDING` order.

> "Placing an order calls book-service through Feign to check stock, inside a Resilience4j circuit
> breaker."

2. **Kill book-service** (`docker compose stop book-service`) and place another order.

> "With book-service down, the breaker opens and the order path degrades gracefully instead of hanging
> — that's the resilience test in `OrderResilienceIntegrationTest`."

3. Restart it (`docker compose start book-service`).

## 6. Pay → events → consumers (90s)

1. **Pay** the pending order → status flips to `PAID`/`SUCCESS`.
2. Try to pay the **same** order again → rejected.

> "Payment enforces one-payment-per-order with a unique constraint. Paying publishes a
> `PaymentCompleted` event."

3. Show consumer logs:
```bash
docker compose logs --tail=20 notification-service analytics-service
```

> "Two independent consumer groups react. They're idempotent — each records handled event ids, so
> Kafka's at-least-once redelivery is safe."

## 7. Observability (45s)

Show: `http://localhost:8080/actuator/prometheus` (and one service's endpoint).

> "Every service exposes Micrometer metrics — QPS, error rate, p99 latency, HikariCP pool, circuit
> breaker state, Kafka consumer lag. `docs/MONITORING.md` defines the alerts on them."

## 8. CI/CD (60s)

Show: the **Actions** tab — a green pipeline run.

> "On every push, GitHub Actions runs the whole test suite. Testcontainers runs natively on the Linux
> runner. If a test fails the pipeline goes red and nothing ships."

Show the DoD evidence: the pipeline run that went **red** on a failing test (screenshot), then the
green run after the fix.

> "Green builds tag one image per service by commit SHA, push to GHCR, and the deploy stage is gated
> behind a manual approval before it rolls out to Kubernetes."

## 9. Wrap (30s)

> "So: monolith to microservices, database-per-service, sync + async communication with resilience
> and idempotency, an AWS cover pipeline, full CI/CD with gated deploys, and metrics on everything.
> What I'd improve next is in `docs/IMPROVEMENTS.md`."

## Reset between takes

```bash
docker compose down -v && docker compose up -d   # wipes DB volumes for a clean slate
```
