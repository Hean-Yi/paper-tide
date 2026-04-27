# 当前设计结构

## 1. 设计目标

- 支持学术投稿与双盲评审主流程
- 保证角色边界与操作可审计
- 用 Agent 提供辅助分析，但不替代人工决策

## 2. 领域模型

核心领域对象（Oracle 表）当前更适合按六组理解：

1. 身份与权限
   - `SYS_USER`
   - `SYS_ROLE`
   - `SYS_USER_ROLE`

2. 稿件与版本
   - `MANUSCRIPT`
   - `MANUSCRIPT_VERSION`
   - `MANUSCRIPT_AUTHOR`

3. 评审流程
   - `REVIEW_ROUND`
   - `REVIEW_ASSIGNMENT`
   - `CONFLICT_CHECK_RECORD`
   - `REVIEW_REPORT`
   - `DECISION_RECORD`

4. API 侧分析意图与投影
   - `ANALYSIS_INTENT`
   - `ANALYSIS_PROJECTION`
   - `ANALYSIS_OUTBOX`
   - `ANALYSIS_INBOX`

5. Agent 侧执行平台
   - `EXECUTION_JOB`
   - `EXECUTION_ATTEMPT`
   - `EXECUTION_ARTIFACT`
   - `EXECUTION_OUTBOX`
   - `EXECUTION_INBOX`

6. 横切能力
   - `SYS_NOTIFICATION`
   - `AUDIT_LOG`

说明：历史 `AGENT_ANALYSIS_TASK` / `AGENT_ANALYSIS_RESULT` / `AGENT_FEEDBACK` 仍可能出现在旧迁移或测试清理路径中，但当前设计主轴已经转到 analysis intent / projection 与 execution job 模型。

## 3. 状态机设计（当前实现）

当前状态机既包含业务工作流状态，也包含 analysis / execution 平台状态。核心状态包含：

- 稿件状态：`DRAFT`、`SUBMITTED`、`UNDER_SCREENING`、`UNDER_REVIEW`、`ACCEPTED`、`REJECTED`、`REVISION_REQUIRED`
- 分配状态：`ASSIGNED`、`ACCEPTED`、`IN_REVIEW`、`SUBMITTED`
- 分析业务状态：`REQUESTED`、`AVAILABLE`、`FAILED_VISIBLE`、`SUPERSEDED`
- 执行作业状态：`QUEUED`、`DISPATCHED`、`RUNNING`、`SUCCEEDED`、`FAILED_RETRYABLE`、`FAILED_TERMINAL`、`DEAD_LETTERED`

当前状态机实现分布在业务 service、analysis domain 和 agent_platform state machine 中，已经比早期字符串驱动模型更清晰，但跨服务状态仍需要通过消息和投影保持一致。

## 4. 权限设计

角色集合：`AUTHOR`、`REVIEWER`、`CHAIR`、`ADMIN`

权限原则：

- Author 仅管理本人稿件
- Reviewer 仅访问被分配稿件
- Chair 负责轮次与决策
- Admin 负责平台管理能力

实现路径：

- Spring Security 路由授权（`/api/**`）
- `RoleGuard` 补充业务层角色校验（如 chair-or-admin）
- Analysis projection 的可见性策略限制 raw / redacted 结果的读取范围

## 5. Analysis / Agent 平台设计

分析类型：

- `SCREENING`
- `REVIEWER_ASSIST`
- `CONFLICT_ANALYSIS`

主处理链路：

1. API 按业务锚点创建 `ANALYSIS_INTENT`
2. API 发布分析请求并等待完成事件
3. Agent 平台将请求映射为 `EXECUTION_JOB`，按状态机执行对应 handler
4. Agent 产出执行尝试、结果产物和完成消息
5. API 更新 `ANALYSIS_PROJECTION`，对前端暴露受角色限制的读取视图

## 6. 当前设计风险

- 状态机逻辑分散，新增状态时容易遗漏
- 旧分析模型与新平台模型在仓库中短期共存，阅读和清理时要明确主次
- 完整的消息驱动验证依赖 Oracle 与 RabbitMQ 同时可用
- 部分前端交互和聚合查询仍需继续做产品化打磨

风险细项和优先级见根目录 `TODO.md`。
