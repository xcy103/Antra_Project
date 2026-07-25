# ROADMAP — 阶段计划

> 规则：**一次只做一个 Phase。** 每个 Phase 结束必须停下来人工验收，通过后才开下一个。
> 当前进度记录在 `PROGRESS.md`。

## 进度总览

| Phase | 对应文档步骤 | 状态 |
|---|---|---|
| 0 | 环境与仓库初始化 | ⬜ 未开始 |
| 1 | Step 1 单体骨架 + AOP | ⬜ |
| 2 | Step 2 数据层 | ⬜ |
| 3 | Step 3 认证与安全 | ⬜ |
| 4 | Step 4 测试体系 | ⬜ |
| 5 | Step 5 拆微服务 | ⬜ |
| 6 | Step 6 配置中心 | ⬜ |
| 7 | Step 7 Kafka | ⬜ |
| 8 | Step 8 API Gateway | ⬜ |
| 9 | Step 9 AWS S3/Lambda/DynamoDB | ⬜ |
| 10 | Step 10 容器化与 K8s | ⬜ |
| 11 | Step 11 CI/CD 与监控 | ⬜ |
| 12 | 交付物（视频/架构图/改进说明/前端） | ⬜ |

---

## Phase 0 — 环境与仓库初始化

**做**：`git init` + `.gitignore`；确认 JDK 17 / Maven / Docker 可用；`docker-compose.yml` 只起一个 PostgreSQL；空的 Spring Boot 工程能 `mvn spring-boot:run` 起来并返回 200。

**不做**：任何业务代码、任何实体、任何数据库表。

**DoD**：`mvn clean verify` 通过；`docker compose up -d postgres` 起得来；`curl localhost:8080/actuator/health` 返回 UP。

---

## Phase 1 — 单体骨架（Step 1）

**做**
- 目录结构：`controller / service / repository / entity / dto / exception / aop`
- `Book` 实体 + `BookRepository` + `BookService`(接口) + `BookServiceImpl`
- 5 个 CRUD 端点（此阶段全部 PUBLIC）：`GET /api/books`、`GET /api/books/{id}`、`POST`、`PUT`、`DELETE`
- `BookRequestDto`（带 `@NotBlank`/`@Positive` 等校验）、`BookResponseDto`
- `GlobalExceptionHandler` + `ResourceNotFoundException` + `ErrorResponse`，映射 400/404/409
- `LoggingAspect`：`@Around` 切 service 层，记录方法名、参数、耗时
- `application.yml` + `application-dev.yml` + `application-prod.yml` 三套 profile

**不做**
- ❌ 不加 Spring Security / JWT（Phase 3）
- ❌ 不建 Author 实体、不写 Flyway（Phase 2）
- ❌ 不加 Kafka / Feign / Gateway 任何依赖
- ❌ 不写 Dockerfile

**测试要求**：`BookServiceImplTest`（Mockito mock repository），覆盖创建成功、查不到抛 `ResourceNotFoundException`、参数非法。

**DoD**：应用能起；5 个端点用 curl/Postman 全部走通；层次干净（controller 无 repository 依赖）；异常返回结构化 JSON；日志里能看到 AOP 打的方法耗时。

---

## Phase 2 — 数据层（Step 2）

**做**
- 切到 Docker PostgreSQL，`ddl-auto: validate`
- `Author` 实体，`Book` 加 `@ManyToOne → Author`
- Flyway `V1__init.sql`：author / book 建表，含 PK、FK、`isbn` 唯一、`stock >= 0` CHECK
- 在 `book.title` 或 `book.author_id` 上建索引，**并在 `02-DESIGN.md` 写清楚为什么**
- 多步写操作加 `@Transactional`
- 故意造一个 N+1（列作者及其书），开 SQL 日志确认，再用 `JOIN FETCH` / EntityGraph 修掉，**前后 SQL 条数记进设计文档**
- 对最重的查询跑 `EXPLAIN ANALYZE`，把执行计划贴进设计文档，确认走了索引
- `Book.stock` 加 `@Version` 乐观锁

**不做**
- ❌ 不碰安全 / 用户
- ❌ 不建 order / payment 表（Phase 5 才有各自服务）
- ❌ 不为了"能跑"退回 `ddl-auto: update`

**测试要求**：`BookRepositoryTest`（`@DataJpaTest` + Testcontainers PostgreSQL）；一个并发更新库存的测试证明乐观锁生效（抛 `OptimisticLockException`）。

**DoD**：数据真落 PostgreSQL；索引有理由；事务保护多步写；能展示 EXPLAIN 计划和 N+1 修复前后的 SQL 对比。

---

## Phase 3 — 认证与安全（Step 3）

**做**
- `User` 实体（username / email / passwordHash / role）+ `UserRepository.findByUsername`
- `AuthController`：`POST /api/auth/register`、`POST /api/auth/login`、`GET /api/auth/me`
- BCrypt 加密密码（`PasswordEncoderConfig`）
- `JwtUtil`（签发+校验，含过期）、`JwtAuthenticationFilter`、`CustomUserDetailsService`
- `SecurityConfig`：无状态 filter chain；书目读 PUBLIC，写 ADMIN
- Flyway `V2__users.sql`

**不做**
- ❌ OAuth2 Google 登录（可选项，放 BACKLOG，等主线做完再说）
- ❌ 不把 JWT secret 硬编码进 yml

**测试要求**：无 token 访问受保护端点返回 401；USER 访问 ADMIN 端点返回 403；过期 token 被拒；密码在库里是 hash 不是明文。

**DoD**：注册登录走通；token 过期生效；能解释服务端如何校验 token（签名、过期、claims）。

---

## Phase 4 — 测试体系（Step 4）

**做**
- 补齐四层测试：`service`(Mockito)、`controller`(`@WebMvcTest`)、`repository`(`@DataJpaTest`)、`integration`(`@SpringBootTest` + Testcontainers)
- 一条端到端集成测试：注册 → 登录 → 建书(ADMIN) → 查书
- 安全测试纳入套件
- 生成覆盖率报告（JaCoCo）

**不做**
- ❌ 不为凑覆盖率写无断言的测试
- ❌ 不改实现去迁就测试

**DoD**：`mvn clean verify` 一条命令跑完全绿；故意改坏一处业务逻辑必须有测试变红（做一次这个验证并记录）。

---

## Phase 5 — 拆微服务（Step 5）

**做**：拆 `user-service` / `book-service` / `order-service` / `payment-service`，各自独立 PostgreSQL 库；order→book 用 OpenFeign（显式超时）+ Resilience4j 熔断/重试/降级；JWT 透传；order/payment 表建起来。

**不做**：❌ 此阶段不引入 Kafka（Phase 7）、不引入 Gateway（Phase 8）、不引入 Config Server（Phase 6）；服务间不共享数据库、不建跨库外键。

**验收演示**：**杀掉 book-service，下单请求必须优雅降级而不是级联超时。** 这是本阶段的核心验收动作。

**设计产出**：Saga / 最终一致性方案写进 `02-DESIGN.md`（订单+支付+库存的补偿路径）。

---

## Phase 6 — 配置中心（Step 6）

**做**：`config-server`(`@EnableConfigServer`) + `config-repo`（每服务一个 yml + 共享 application.yml）；各服务用 `spring.config.import` 拉配置；密钥走环境变量。
**不做**：❌ 不把密码明文放进 config-repo。
**设计产出**：写一段"这在真实 K8s 部署里如何被 ConfigMap/Secret 取代"——文档明说面试会问。

---

## Phase 7 — Kafka（Step 7）

**做**：Docker 起 Kafka；order 发 `OrderPlaced`，payment 发 `PaymentCompleted`；新建 `notification-service`、`analytics-service`，两个**不同 consumer group** 消费同一 topic；消息按 orderId 做 key 保序；消费者幂等；DLQ（challenge）。
**不做**：❌ 这两个服务不暴露业务 REST API，只有 `/actuator/health`。
**测试要求**：重复投递同一条消息，断言不会重复处理（幂等测试）。

---

## Phase 8 — API Gateway（Step 8）

**做**：Spring Cloud Gateway 按路径路由四个服务；边缘校验 JWT 并透传身份；CORS 统一配在这里。
**不做**：❌ Gateway 里不写任何业务逻辑。
**DoD**：所有客户端流量走单一地址；未认证请求在边缘被拒。

---

## Phase 9 — AWS（Step 9）

**Feature A 封面**：S3 上传 → Lambda(`CoverImageHandler`) → 写 DynamoDB `CoverMetadata` → SNS/SES 发邮件。用 bookId 做确定性 key + 条件写，保证重复事件不产生重复记录和重复邮件。
**Feature B 浏览历史**：登录用户 `GET /api/books/{id}` 时**异步**写 DynamoDB `UserBrowsingHistory`（PK=userId, SK=viewedAt），提供 `GET /api/books/me/history` 按时间倒序；设 TTL 30 天。
**兜底**：没有 AWS 账号就写完整流程设计文档 + LocalStack 本地模拟（文档明确允许）。**这个决定要先问我。**

---

## Phase 10 — 容器化与编排（Step 10）

**做**：每服务多阶段 Dockerfile（JDK 构建 → JRE 运行）+ `.dockerignore`；`docker-compose.yml` 一键起全栈（所有服务 + 各 PG + Kafka）；`k8s/` 下每服务 Deployment+Service、ConfigMap、Secret、liveness/readiness 探针接 Actuator；HPA（challenge）。
**DoD**：`docker compose up` 全栈可用。

---

## Phase 11 — CI/CD 与监控（Step 11）

**做**：`.github/workflows/ci.yml` — push/PR 触发 build → test → 构建镜像 → 按 commit SHA 打 tag 推送 →（部署或设计带人工审批的部署阶段）；测试失败必须让流水线红。各服务开 Actuator 健康端点。定义并写下每服务监控指标（QPS、错误率、p99、Kafka consumer lag、DB 连接数）与告警项。
**DoD**：故意提交一个失败测试，流水线必须变红（做一次验证并截图存证）。

---

## Phase 12 — 交付物

- [ ] 架构图（含前端/后端/数据库/AWS/K8s 全景）
- [ ] AI 生成的 React 前端，调已文档化的 API
- [ ] 演示视频
- [ ] "还需改进什么"说明文档
- [ ] 检查 git log 是否清晰体现了逐步演进
