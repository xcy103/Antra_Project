# Capstone 项目解读（读完这份就懂了在做什么）

## 一句话

做一个**在线书店平台**：从一个 Spring Boot 单体应用开始，逐步拆成微服务，接入 Kafka、AWS、Kubernetes、CI/CD。**重点不是书店业务，而是把课程里所有技术栈都用上一遍。**

文档明确说了两件事：

- 不一定非要做书店，但**必须用上所有讨论过的技术，并且必须包含 AWS 服务**。
- **前端不用手写**，可以让 AI 生成一个简单的 React/HTML 页面调你的 API。精力全放后端。

## 最终形态

```
                [ 前端（AI 生成，可选）]
                          |
                   [ API Gateway ]        路由 + 边缘鉴权
                          |
   ┌──────────┬───────────┴───────────┬──────────────┐
user-service  book-service      order-service   payment-service
   (PG)          (PG)                (PG)            (PG)
                   ^                  |
                   |  Feign+Resilience4j  发布 OrderPlaced / PaymentCompleted
                   |                  v
                                  [ Kafka ]
                                   /      \
                    notification-service  analytics-service

所有服务从 [ config-server ] 读配置（Spring Cloud Config）

封面上传（Serverless）:  S3 --事件--> Lambda --> DynamoDB (封面元数据)
                                        └--> SNS/SES --> 邮件"封面已处理"
DynamoDB 还存: 用户浏览历史（最近查看的书）

横切: AOP 日志/计时 · Actuator + CloudWatch · Docker · K8s/EKS · CI/CD
```

## 系统里的三种角色

| 角色 | 含义 |
|---|---|
| PUBLIC | 不用登录，任何人（含匿名访客） |
| USER | 注册并登录的顾客（JWT 里 role=USER） |
| ADMIN | 员工账号（role=ADMIN），管理目录、看所有订单 |

## 11 个步骤在干什么

| 步骤 | 主题 | 核心产出 |
|---|---|---|
| 1 | 单体骨架 | Spring Boot 分层（controller/service/repository/entity/dto/exception/aop）、Book CRUD、全局异常处理、AOP 日志切面 |
| 2 | 数据层 | PostgreSQL、Book+Author 建模、Flyway 迁移、索引、`@Transactional`、修 N+1、EXPLAIN ANALYZE、`@Version` 乐观锁 |
| 3 | 认证与安全 | User 实体、注册/登录、BCrypt、JWT 签发与校验、无状态过滤器链、USER/ADMIN 权限 |
| 4 | 测试 | Mockito 单测、`@WebMvcTest`、`@DataJpaTest`、Testcontainers 的 `@SpringBootTest` 集成测试、安全测试 |
| 5 | 拆微服务 | 拆成 user/book/order/payment 四个服务，各自独立数据库；order→book 用 OpenFeign + Resilience4j 熔断降级；JWT 透传；Saga/最终一致性 |
| 6 | 集中配置 | Spring Cloud Config Server + config-repo，各服务启动时拉配置；密钥走环境变量 |
| 7 | Kafka 异步 | order 发 `OrderPlaced`，payment 发 `PaymentCompleted`；notification + analytics 两个消费组各自消费；按 orderId 分区保序；消费幂等；DLQ |
| 8 | API Gateway | Spring Cloud Gateway 统一入口，按路径路由，边缘校验 JWT，统一 CORS |
| 9 | 文件处理（AWS） | **A**: S3 上传封面 → Lambda 触发 → 写 DynamoDB 元数据 → SNS/SES 发邮件，全流程幂等。**B**: 登录用户查看图书时异步写 DynamoDB 浏览历史（PK=userId, SK=viewedAt, TTL 30 天），提供"最近浏览"接口 |
| 10 | 容器化编排 | 每服务多阶段 Dockerfile、docker-compose 一键起全栈、K8s Deployment/Service/ConfigMap/Secret、存活就绪探针、HPA |
| 11 | CI/CD 与监控 | GitHub Actions：build → test → 打镜像 → 推送 → 部署；Actuator 健康端点；定义监控指标（QPS、错误率、p99、Kafka lag、连接池）与告警 |

## 数据库设计（文档给的参考，可自行调整）

**user-service / users**：id, username(UQ), email(UQ), password_hash(BCrypt), role, created_at

**book-service / author**：id, name
**book-service / book**：id, title(建索引), author_id(FK→author, 建索引), isbn(UQ), price, stock(CHECK ≥0), cover_url, version(乐观锁), created_at

**order-service / orders**：id, user_id, status(PENDING/PAID/CANCELLED/SHIPPED), total_price, created_at
**order-service / order_item**：id, order_id(FK→orders), book_id, quantity, unit_price(下单时价格快照)

**payment-service / payment**：id, order_id(UQ，一单一付), amount, status(SUCCESS/FAILED), paid_at

> **关键原则 — Database per Service**：`orders.user_id` 和 `order_item.book_id` 只是普通 id 值，**不是外键**。跨服务不做 DB 外键，要数据就调对方 API。

**DynamoDB 两张表**：
- `CoverMetadata`：PK=bookId，属性 s3Key/contentType/width/height/sizeBytes/processedAt
- `UserBrowsingHistory`：PK=userId, SK=viewedAt(epoch ms)，属性 bookId/bookTitle，TTL=expireAt

## 完整 API 清单

**user-service**

| Method | Path | 功能 | 角色 |
|---|---|---|---|
| POST | /api/auth/register | 注册 | PUBLIC |
| POST | /api/auth/login | 登录，返回 JWT | PUBLIC |
| GET | /api/users/me | 当前用户资料 | USER/ADMIN |
| GET | /api/users | 用户列表 | ADMIN |
| GET | /api/users/{id} | 按 id 查用户 | ADMIN |

**book-service**

| Method | Path | 功能 | 角色 |
|---|---|---|---|
| GET | /api/books | 列表/搜索（分页+关键字） | PUBLIC |
| GET | /api/books/{id} | 单本详情 | PUBLIC |
| POST | /api/books | 新建 | ADMIN |
| PUT | /api/books/{id} | 更新 | ADMIN |
| DELETE | /api/books/{id} | 删除 | ADMIN |
| GET | /api/books/{id}/stock | 查库存（order-service 内部调用） | PUBLIC |
| POST | /api/books/{id}/cover | 上传封面 / 获取上传 URL | ADMIN |
| GET | /api/books/{id}/cover | 封面 URL + 元数据 | PUBLIC |
| GET | /api/books/me/history | 我最近浏览的书 | USER |

**order-service**

| Method | Path | 功能 | 角色 |
|---|---|---|---|
| POST | /api/orders | 下单（查库存、发 OrderPlaced） | USER |
| GET | /api/orders | 我的订单列表 | USER |
| GET | /api/orders/{id} | 订单详情 | USER(本人)/ADMIN |
| GET | /api/orders/all | 全部订单 | ADMIN |
| PUT | /api/orders/{id}/cancel | 取消订单（未发货） | USER(本人)/ADMIN |

**payment-service**

| Method | Path | 功能 | 角色 |
|---|---|---|---|
| POST | /api/payments | 支付订单（成功后发 PaymentCompleted） | USER(本人订单) |
| GET | /api/payments/{orderId} | 查支付状态 | USER(本人)/ADMIN |

**notification-service / analytics-service**：无业务 API，只是 Kafka 消费者，最多暴露 `/actuator/health`
**api-gateway**：无业务 API，负责路由 + 边缘 JWT 校验
**config-server**：无业务 API，启动时给各服务发配置

**Gateway 路由表**

| 入口路径 | 转发到 | 说明 |
|---|---|---|
| /api/auth/**, /api/users/** | user-service | auth 公开，其余需 token |
| /api/books/** | book-service | GET 公开，写操作 ADMIN |
| /api/orders/** | order-service | 需 token |
| /api/payments/** | payment-service | 需 token |

## 最终要交什么

1. **Git 源码**，且 commit 历史要能体现按步骤演进的过程（不能一把梭一个 commit）
2. **一段演示视频**，展示平台怎么用
3. **改进点说明**：基于你的完成度，写清楚哪些地方还需要改进
4. **架构图**：高层架构，含前端、后端、数据库，整体串起来

## 评估怎么看

每一步都有 **Definition of Done**，本质是"能跑 + 能解释"。文档反复强调"be able to explain why"——比如为什么这个列加索引、JWT 怎么被服务端校验、为什么 partition key 选 userId、N+1 是怎么修的。**面试导向，不只是代码跑通。**

## 几个容易踩的坑（提前提醒）

- **Step 5 拆服务时的分布式事务**：下单跨 order/payment/book 三个服务，没有单一 ACID 事务，文档把 Saga 列为 challenge，需要提前想好补偿逻辑。
- **Kafka 至少一次投递**：消费者必须幂等，否则重复消费会重复发通知/重复计数。
- **Step 9 需要真 AWS 账号**。如果没有，文档允许交一份流程设计文档，用 LocalStack 本地模拟算加分。
- **commit 历史是评分项**，从第一天就要按步骤提交，不能最后补。
