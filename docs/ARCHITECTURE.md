# 系统架构

## 1. 总体架构

PaperTide Review 当前采用三应用服务 + Oracle + 本地可选 RabbitMQ 的架构，并且已经从旧的“API 直连 Agent 任务接口”演进到“API 侧业务意图 + Agent 侧执行平台”的分治模型。

- `apps/api`：Spring Boot 主业务系统，负责认证、投稿、评审、主席决策、分析意图与结果投影
- `apps/web`：Vue 3 前端，负责角色化页面、路由守卫与用户交互
- `services/agent`：FastAPI 承载的分析执行平台，负责执行状态机、处理器注册、工作流调用与结果产出
- `database/oracle`：业务表、分析平台表、执行平台表、审计与通知表
- `scripts/rabbitmq-up.sh`：本地消息基础设施启动脚本，供开发与验证路径使用

## 2. 运行拓扑

```text
[Browser]
  |
  v
[Vue Web: apps/web] -- JWT --> [Spring API: apps/api] -- JDBC --> [Oracle]
                   |
                   | analysis outbox / inbox
                   v
                 [RabbitMQ]
                   |
                   v
            [FastAPI Agent Platform: services/agent]
                   |
                   | execution job store / artifacts
                   v
                [Oracle]
```

关键边界：

- 前端只通过 API 访问业务数据，不直连 Oracle
- API 拥有业务身份、权限、工作流状态与分析结果可见性
- Agent 拥有执行作业、尝试次数、处理器选择和产物生成
- Agent 平台在缺少数据库配置时可以退化为内存执行存储；配置完整时使用 Oracle 持久化执行状态

## 3. 服务职责边界

### 3.1 API（Spring Boot）

- 账号认证与 JWT 鉴权
- 投稿与版本管理（创建版本、上传 PDF、提交）
- 评审轮次、分配、评审报告、冲突检查
- 主席决策与工作台查询
- 创建 `ANALYSIS_INTENT` / `ANALYSIS_PROJECTION`，发布分析请求，消费完成事件

### 3.2 Web（Vue 3）

- 登录态管理与角色守卫
- Author / Reviewer / Chair / Admin 的工作流页面
- 调用 API 发起 screening、reviewer assist、conflict analysis
- 呈现 redacted 结果、冲突摘要、决策工作台和管理员监控视图

### 3.3 Agent 平台（FastAPI）

- 在 `app/main.py` 中组装 `AgentPlatformRuntime`
- 用 `ExecutionStateMachine` 管理 `QUEUED`、`RUNNING`、`SUCCEEDED`、`FAILED_*` 等执行状态
- 通过 `AnalysisHandlerRegistry` 选择 `reviewer_assist`、`conflict_analysis`、`screening` 处理器
- 使用 `ProviderExecutor`、`workflows/*`、`pdf_tools.py`、`redaction.py` 产出结构化分析结果

## 4. 数据流与一致性

### 4.1 投稿与评审主链路

1. Author 创建稿件、版本并提交。
2. Chair 进入 screening queue，创建评审轮次并分配 Reviewer。
3. Reviewer 接受任务、在线阅读稿件并提交评审报告。
4. Chair 在 decision workbench 汇总评审、冲突分析和辅助结果后做最终决策。
5. API 在事务内更新业务状态，并通过审计与通知服务记录横切行为。

### 4.2 分析异步链路

1. API 按业务锚点创建分析意图：`SCREENING`、`REVIEWER_ASSIST`、`CONFLICT_ANALYSIS`。
2. API 记录 outbox 消息，向执行平台发布分析请求。
3. Agent 平台将请求转换为 `EXECUTION_JOB`，通过状态机和处理器执行分析。
4. Agent 平台写入执行尝试、产物与执行 outbox / inbox，并发布完成结果。
5. API 消费完成消息，更新 `ANALYSIS_PROJECTION`，向前端提供角色受限的读取视图。

## 5. 安全架构

- JWT 无状态会话
- `SecurityConfig` 对 `/api/**` 使用显式路径授权，并以 `anyRequest().denyAll()` 收口
- `RoleGuard` 在业务层补充 Author / Reviewer / Chair / Admin 的领域校验
- Reviewer 只能读取 assignment 范围内且经过脱敏的分析结果
- Agent 平台当前不再作为前台业务 HTTP 接口暴露任务创建端点；公开健康检查入口为 `/health`

## 6. 当前架构特点

- 业务意图与执行作业解耦，API 与 Agent 各自拥有明确主权
- 双盲展示策略由投影可见性控制，而不是前端约定
- Oracle 模式和内存退化模式都能支撑本地开发
- 本地启动与测试脚本已经把 Oracle / RabbitMQ 预热纳入常规路径

## 7. 当前约束

- 仓库中仍保留一部分旧工作流代码和历史测试结果文档，阅读时应以 `analysis/*`、`agent_platform/*` 与最新计划文件为准
- `scripts/test-all.sh` 当前对 Agent 只跑健康检查子集；完整 Agent 平台测试需要单独执行 `pytest tests/`
- 运行消息驱动全链路时，需要本地 Oracle 与 RabbitMQ 都可用

详细实施状态以 `docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md` 为准。
