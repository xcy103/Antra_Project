# Capstone Project Overview (read this and you'll understand what we're building)

## In one sentence

Build an **online bookstore platform**: start from a Spring Boot monolith, then gradually split it into microservices, wiring in Kafka, AWS, Kubernetes, and CI/CD. **The point is not the bookstore business — it's exercising every part of the course's tech stack.**

The source document is explicit about two things:

- The bookstore isn't mandatory, but you **must use all of the discussed technologies, and it must include AWS services**.
- **You don't have to hand-write a frontend** — you may let AI generate a simple React/HTML page that calls your API. Spend your energy on the backend.

## Target architecture

```
                [ Frontend (AI-generated, optional) ]
                          |
                   [ API Gateway ]        routing + edge auth
                          |
   ┌──────────┬───────────┴───────────┬──────────────┐
user-service  book-service      order-service   payment-service
   (PG)          (PG)                (PG)            (PG)
                   ^                  |
                   |  Feign+Resilience4j  publishes OrderPlaced / PaymentCompleted
                   |                  v
                                  [ Kafka ]
                                   /      \
                    notification-service  analytics-service

All services read config from [ config-server ] (Spring Cloud Config)

Cover upload (Serverless):  S3 --event--> Lambda --> DynamoDB (cover metadata)
                                        └--> SNS/SES --> "cover processed" email
DynamoDB also stores: user browsing history (recently viewed books)

Cross-cutting: AOP logging/timing · Actuator + CloudWatch · Docker · K8s/EKS · CI/CD
```

## Three roles in the system

| Role | Meaning |
|---|---|
| PUBLIC | No login required; anyone (including anonymous visitors) |
| USER | Registered and logged-in customer (role=USER in the JWT) |
| ADMIN | Staff account (role=ADMIN); manages the catalog, sees all orders |

## What the 11 steps are doing

| Step | Theme | Core deliverable |
|---|---|---|
| 1 | Monolith skeleton | Spring Boot layering (controller/service/repository/entity/dto/exception/aop), Book CRUD, global exception handling, AOP logging aspect |
| 2 | Data layer | PostgreSQL, Book+Author modeling, Flyway migrations, indexes, `@Transactional`, fixing N+1, EXPLAIN ANALYZE, `@Version` optimistic locking |
| 3 | Auth & security | User entity, register/login, BCrypt, JWT issuance & validation, stateless filter chain, USER/ADMIN authorization |
| 4 | Testing | Mockito unit tests, `@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest` integration tests with Testcontainers, security tests |
| 5 | Split into microservices | Split into user/book/order/payment services, each with its own database; order→book via OpenFeign + Resilience4j (circuit breaker/fallback); JWT propagation; Saga/eventual consistency |
| 6 | Centralized config | Spring Cloud Config Server + config-repo; each service pulls config on startup; secrets via environment variables |
| 7 | Kafka async | order publishes `OrderPlaced`, payment publishes `PaymentCompleted`; notification + analytics as two separate consumer groups; partition by orderId for ordering; idempotent consumers; DLQ |
| 8 | API Gateway | Spring Cloud Gateway as the single entry point, path-based routing, edge JWT validation, unified CORS |
| 9 | File processing (AWS) | **A**: upload cover to S3 → Lambda trigger → write DynamoDB metadata → SNS/SES email, fully idempotent. **B**: when a logged-in user views a book, asynchronously write DynamoDB browsing history (PK=userId, SK=viewedAt, TTL 30 days) and expose a "recently viewed" endpoint |
| 10 | Containerization & orchestration | Multi-stage Dockerfile per service, docker-compose to bring up the whole stack, K8s Deployment/Service/ConfigMap/Secret, liveness/readiness probes, HPA |
| 11 | CI/CD & monitoring | GitHub Actions: build → test → build image → push → deploy; Actuator health endpoints; define monitoring metrics (QPS, error rate, p99, Kafka lag, connection pool) and alerts |

## Database design (reference from the source doc, adjustable)

**user-service / users**: id, username(UQ), email(UQ), password_hash(BCrypt), role, created_at

**book-service / author**: id, name
**book-service / book**: id, title(indexed), author_id(FK→author, indexed), isbn(UQ), price, stock(CHECK ≥0), cover_url, version(optimistic lock), created_at

**order-service / orders**: id, user_id, status(PENDING/PAID/CANCELLED/SHIPPED), total_price, created_at
**order-service / order_item**: id, order_id(FK→orders), book_id, quantity, unit_price(price snapshot at order time)

**payment-service / payment**: id, order_id(UQ, one payment per order), amount, status(SUCCESS/FAILED), paid_at

> **Key principle — Database per Service**: `orders.user_id` and `order_item.book_id` are plain id values, **not foreign keys**. No cross-service DB foreign keys; to get another service's data, call its API.

**Two DynamoDB tables**:
- `CoverMetadata`: PK=bookId, attributes s3Key/contentType/width/height/sizeBytes/processedAt
- `UserBrowsingHistory`: PK=userId, SK=viewedAt(epoch ms), attributes bookId/bookTitle, TTL=expireAt

## Full API list

**user-service**

| Method | Path | Function | Role |
|---|---|---|---|
| POST | /api/auth/register | Register | PUBLIC |
| POST | /api/auth/login | Login, returns JWT | PUBLIC |
| GET | /api/users/me | Current user profile | USER/ADMIN |
| GET | /api/users | User list | ADMIN |
| GET | /api/users/{id} | Get user by id | ADMIN |

**book-service**

| Method | Path | Function | Role |
|---|---|---|---|
| GET | /api/books | List/search (paged + keyword) | PUBLIC |
| GET | /api/books/{id} | Single book detail | PUBLIC |
| POST | /api/books | Create | ADMIN |
| PUT | /api/books/{id} | Update | ADMIN |
| DELETE | /api/books/{id} | Delete | ADMIN |
| GET | /api/books/{id}/stock | Check stock (called internally by order-service) | PUBLIC |
| POST | /api/books/{id}/cover | Upload cover / get upload URL | ADMIN |
| GET | /api/books/{id}/cover | Cover URL + metadata | PUBLIC |
| GET | /api/books/me/history | Books I recently viewed | USER |

**order-service**

| Method | Path | Function | Role |
|---|---|---|---|
| POST | /api/orders | Place order (check stock, publish OrderPlaced) | USER |
| GET | /api/orders | My order list | USER |
| GET | /api/orders/{id} | Order detail | USER(owner)/ADMIN |
| GET | /api/orders/all | All orders | ADMIN |
| PUT | /api/orders/{id}/cancel | Cancel order (not yet shipped) | USER(owner)/ADMIN |

**payment-service**

| Method | Path | Function | Role |
|---|---|---|---|
| POST | /api/payments | Pay for an order (publish PaymentCompleted on success) | USER(own order) |
| GET | /api/payments/{orderId} | Check payment status | USER(owner)/ADMIN |

**notification-service / analytics-service**: no business API, just Kafka consumers, at most expose `/actuator/health`
**api-gateway**: no business API, handles routing + edge JWT validation
**config-server**: no business API, serves config to services on startup

**Gateway routing table**

| Entry path | Forwards to | Notes |
|---|---|---|
| /api/auth/**, /api/users/** | user-service | auth is public, the rest need a token |
| /api/books/** | book-service | GET public, writes ADMIN |
| /api/orders/** | order-service | needs a token |
| /api/payments/** | payment-service | needs a token |

## Final deliverables

1. **Git source code**, with a commit history that shows the step-by-step evolution (not one big commit).
2. **A demo video** showing how the platform is used.
3. **Improvement notes**: based on your completion level, spell out what still needs work.
4. **Architecture diagram**: high-level architecture including frontend, backend, database, all tied together.

## How it's evaluated

Every step has a **Definition of Done** — essentially "it runs + you can explain it". The source doc repeatedly stresses "be able to explain why" — e.g. why this column is indexed, how the server validates a JWT, why the partition key is userId, how the N+1 was fixed. **Interview-oriented, not just code that runs.**

## A few easy traps (heads-up)

- **Distributed transactions when splitting services in Step 5**: placing an order spans order/payment/book — three services, no single ACID transaction. The doc lists Saga as a challenge; plan compensation logic up front.
- **Kafka at-least-once delivery**: consumers must be idempotent, or duplicate consumption means duplicate notifications/double counting.
- **Step 9 needs a real AWS account.** If you don't have one, the doc allows submitting a full design document; simulating locally with LocalStack is a bonus.
- **Commit history is a grading item** — commit step by step from day one; don't backfill at the end.
