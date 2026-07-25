# CLAUDE.md — 开发规范与边界

> 这个文件约束 AI agent 在本仓库的行为。**每次开工前先读这里和 `docs/01-ROADMAP.md`，确认当前阶段，只做当前阶段的事。**

## 项目

Bookstore 微服务 capstone。需求源文档：`docs/capstone-project.docx`，中文解读：`docs/00-项目解读.md`。

## 技术栈（固定，不得擅自更换）

- Java 17，Maven，Spring Boot 3.x
- PostgreSQL（Docker 运行），Flyway 管理 schema
- JUnit 5 + Mockito + Testcontainers
- 后续阶段：OpenFeign、Resilience4j、Spring Cloud Config、Spring Cloud Gateway、Kafka、AWS（S3/Lambda/DynamoDB/SNS）、Docker、Kubernetes、GitHub Actions

## 铁律（Hard Rules）

### 1. 阶段边界
- **只实现当前 Phase 的内容。** 不提前引入下一阶段的依赖、注解、目录或配置。
  - 例：Phase 1 不准出现 Spring Security、Kafka、Feign 的任何代码或 pom 依赖。
- 如果实现当前任务时"顺手"想加下阶段的东西 → **停下，记到 `docs/BACKLOG.md`，不写代码。**
- 每个 Phase 完成后必须停下来等人工确认，不得自动进入下一 Phase。

### 2. 测试是完成的一部分，不是补充
- **没有测试的功能视为未完成。** 不允许"先实现，测试后面补"。
- 测试必须验证**真实行为**，不允许为了让测试变绿而写空断言、断言 `true`、mock 掉被测对象本身、或删改测试来迁就实现。
- 每个 Phase 交付前必须能一条命令跑完并全绿：`mvn clean verify`
- 测试要覆盖 **happy path + 至少一个失败/边界路径**（如：库存不足、资源不存在、参数校验失败、无权限）。
- 修 bug 时：**先写一个能复现该 bug 的失败测试，再改代码让它通过。**

### 2b. 构建验证在本机跑（重要）
- Agent 的沙盒环境**没有 Maven，且 Maven 中央仓库被网络策略屏蔽**，无法下载依赖，因此**agent 跑不了 `mvn clean verify`**。
- 所以流程是：**agent 写实现和测试 → 本人在 IntelliJ 或终端跑构建 → 把失败输出贴回来 → agent 修**。
- Agent **不得**因为自己没法跑构建就宣称"应该能通过"。没跑过就是没验证，必须明说"待本机验证"。
- 每个 Phase 的验收以**本机 `mvn clean verify` 的真实输出**为准。

### 3. 不许伪实现
- 不写 `// TODO: implement` 就当交付；不写返回硬编码假数据的 service 方法。
- 不因为"看起来能跑"就宣告完成 —— 每个 Phase 的 Definition of Done 必须逐条核对。
- 无法完成的部分，明确说"没做/做不了"，写进 `docs/BACKLOG.md`，不要静默跳过。

### 4. Commit 纪律（这是评分项）
- 小步提交，一个逻辑变更一个 commit，能体现按 Phase 演进。
- 格式：`feat(book): add Book CRUD endpoints` / `test(book): add BookServiceImpl unit tests` / `fix(order): prevent negative stock`
- **禁止**把一个 Phase 的所有改动压成一个大 commit。
- 不提交 `.env`、密钥、`target/`、IDE 配置。

### 5. 分层与依赖方向
```
controller → service(接口) → serviceImpl → repository → entity
                    ↑
                   dto（进出参，绝不把 entity 直接暴露给 controller）
```
- Controller **不准**直接注入 Repository。
- Entity **不准**出现在 Controller 的方法签名里，一律走 DTO。
- 依赖注入一律用**构造器注入**，不用 `@Autowired` 字段注入。
- 业务异常抛自定义异常，统一由 `@RestControllerAdvice` 转成 HTTP 状态码。

### 6. 数据库
- Schema 变更**只能**通过 Flyway 迁移脚本（`V{n}__desc.sql`），不准依赖 `ddl-auto: update`（本地开发也用 `validate`）。
- 已提交的迁移脚本**不可修改**，只能新增。
- 跨服务**不建外键**（Database per Service）。`orders.user_id`、`order_item.book_id` 是裸 id。
- 多步写操作必须 `@Transactional`。

### 7. 安全
- 密码只存 BCrypt hash，任何地方不得出现明文密码。
- 密钥/JWT secret/DB 密码走环境变量或配置服务器，**不准硬编码进 yml 提交到 Git**。
- 日志里不准打印 token、密码、完整个人信息。
- **这是公开仓库**，任何提交前都要确认没有夹带凭证。
- AWS 凭证只走本机 `~/.aws/credentials` 或环境变量，代码里一律用 SDK 默认凭证链，**绝不出现 access key / secret key 字面量**。
- 需要占位的地方写 `${ENV_VAR}`，并在 `.env.example` 里列出变量名（不列值）。

### 8. Git 工作流
- 默认分支 `main`。远程仓库是 **GitHub 公开仓库**。
- Agent **只负责本地 commit，不执行 push**。推送由本人在 IntelliJ 或命令行完成。
- 提交时机：**每完成一个可独立描述的 feature/fix/test 就提交一次**，不要攒到 Phase 结束才提交。
- 每个 Phase 结束时额外打一个 tag：`phase-1`、`phase-2`……方便回溯演进过程。
- commit message 用英文，遵循 `type(scope): summary`，type 取 `feat|fix|test|refactor|docs|chore|build|ci`。

## 需要先问我的情况（不要自己决定）

- 要偏离本文档写死的技术栈或版本
- 要改动已经确定的 API 路径、角色权限、数据库表结构
- 一个 Phase 的 Definition of Done 有做不到的项
- 需要新增一个前面没规划过的模块或第三方依赖
- 遇到需要真 AWS 账号/花钱的操作

## 每个 Phase 的交付检查清单

开发者/agent 在宣告某 Phase 完成前，逐条自查：

- [ ] `mvn clean verify` 全绿
- [ ] 该 Phase 的 Definition of Done 每一条都能演示
- [ ] 新增代码有对应测试，含至少一条失败路径
- [ ] 没有引入下一阶段的依赖
- [ ] commit 拆分合理，信息清晰
- [ ] 更新了 `docs/PROGRESS.md`（做了什么、遗留什么、下一步）
- [ ] 能口头解释这一步的技术选型理由（面试导向）

## 目录约定

```
Antra_Project/
├── CLAUDE.md                  # 本文件
├── docs/
│   ├── capstone-project.docx  # 原始需求
│   ├── 00-项目解读.md          # 需求中文解读
│   ├── 01-ROADMAP.md          # 阶段计划（当前进度看这里）
│   ├── 02-DESIGN.md           # 架构与设计决策
│   ├── PROGRESS.md            # 进度日志
│   └── BACKLOG.md             # 被推迟/未完成的事项
└── bookstore/                 # Phase 1-4 单体；Phase 5 后拆为 bookstore-platform/
```
