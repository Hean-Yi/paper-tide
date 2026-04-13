# 当前设计结构

## 1. 设计目标

- 支持学术投稿与双盲评审主流程
- 保证角色边界与操作可审计
- 用 Agent 提供辅助分析，但不替代人工决策

## 2. 领域模型

核心领域对象（Oracle 表）可分为五组：

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

4. Agent 相关
   - `AGENT_ANALYSIS_TASK`
   - `AGENT_ANALYSIS_RESULT`
   - `AGENT_FEEDBACK`

5. 横切能力
   - `SYS_NOTIFICATION`
   - `AUDIT_LOG`

## 3. 状态机设计（当前实现）

当前状态机主要通过 service 层字符串与集合约束实现，核心状态包含：

- 稿件状态：`DRAFT`、`SUBMITTED`、`UNDER_SCREENING`、`UNDER_REVIEW`、`ACCEPTED`、`REJECTED`、`REVISION_REQUIRED`
- 分配状态：`ASSIGNED`、`ACCEPTED`、`IN_REVIEW`、`SUBMITTED`

当前已知设计债务：状态机尚未枚举化，缺少统一 transition table。

## 4. 权限设计

角色集合：`AUTHOR`、`REVIEWER`、`CHAIR`、`ADMIN`

权限原则：

- Author 仅管理本人稿件
- Reviewer 仅访问被分配稿件
- Chair 负责轮次与决策
- Admin 负责平台管理能力

实现路径：

- Spring Security 路由授权（`/api/**`）
- 业务层补充角色校验（如 chair-or-admin）

## 5. Agent 设计

任务类型：

- `SCREENING_ANALYSIS`
- `REVIEW_ASSIST_ANALYSIS`
- `DECISION_CONFLICT_ANALYSIS`

处理步骤：

1. API 组装任务输入
2. Agent 路由到对应工作流
3. 生成 raw + redacted 结果
4. API 持久化并对前端提供查询接口

## 6. 当前设计风险

- 状态机逻辑分散，新增状态时容易遗漏
- 权限守卫存在重复实现
- 部分接口和前端交互存在样板重复
- Agent 缓存复用策略对失败任务不够严格

风险细项和优先级见根目录 `TODO.md`。
