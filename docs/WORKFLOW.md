# 当前 Workflow

## 1. 角色视角总览

### Author

1. 登录
2. 创建稿件与上传版本 PDF
3. 提交版本进入评审流程
4. 跟踪状态与决策结果

### Reviewer

1. 登录后查看被分配任务
2. 接受/拒绝任务
3. 填写并提交评审报告

### Chair

1. 查看 screening queue
2. 创建评审轮次并分配 Reviewer
3. 发起 screening analysis 或 conflict analysis
4. 在 decision workbench 做终审决策

### Admin

1. 监控 Agent 任务与状态
2. 查看系统管理相关能力

## 2. 端到端主流程

```text
Author submit manuscript
  -> Chair starts screening / round
  -> Chair assigns reviewers
  -> Reviewer submits reports
  -> Chair checks conflict + analysis insights
  -> Chair records decision
  -> Manuscript status updated
```

## 3. API Workflow 入口（摘要）

- Auth
  - `POST /api/auth/login`

- Manuscript
  - `POST /api/manuscripts`
  - `POST /api/manuscripts/{id}/versions`
  - `POST /api/manuscripts/{id}/versions/{versionId}/pdf`
  - `POST /api/manuscripts/{id}/versions/{versionId}/submit`

- Review
  - `GET /api/review-assignments`
  - `GET /api/review-assignments/{assignmentId}`
  - `POST /api/review-rounds`
  - `POST /api/review-rounds/{roundId}/assignments`
  - `POST /api/review-assignments/{assignmentId}/accept`
  - `POST /api/review-assignments/{assignmentId}/review-report`
  - `GET /api/review-assignments/{assignmentId}/paper`
  - `GET /api/review-assignments/{assignmentId}/paper/pages/{pageNo}`

- Workflow Query
  - `GET /api/chair/screening-queue`
  - `GET /api/chair/decision-workbench`
  - `GET /api/admin/analysis-monitor`

- Decision
  - `GET /api/decisions`
  - `POST /api/decisions`

- Analysis
  - `POST /api/manuscripts/{manuscriptId}/versions/{versionId}/screening-analysis`
  - `POST /api/review-assignments/{assignmentId}/agent-assist`
  - `GET /api/review-assignments/{assignmentId}/agent-assist`
  - `POST /api/review-rounds/{roundId}/conflict-analysis`

## 4. 分析子流程

当前仓库里的分析请求不再走通用任务 REST 模型，而是按业务锚点直接发起 intent。

### 4.1 Screening

`manuscript version -> analysis intent -> execution job -> projection`

### 4.2 Reviewer Assist

`assignment -> analysis intent -> execution job -> reviewer-scoped projection`

### 4.3 Conflict Analysis

`review round -> analysis intent -> execution job -> chair-facing projection`

## 5. 执行侧工作流阶段

执行平台内部仍会调用工作流组件，典型阶段是：

- `understand`
- `analyze`
- `validate`
- `redact`

不同分析类型复用这些阶段，但由 `agent_platform/handlers/*` 选择具体处理器和上下文装配方式。

## 6. 当前 Workflow 说明

- Screening、Reviewer Assist、Conflict Analysis 都是异步请求，控制器返回 `202 Accepted`
- Reviewer Assist 已有独立读取端点，便于 Reviewer 页面轮询或刷新当前 assignment 的分析状态
- Chair 的 screening queue 和 decision workbench、Admin 的 analysis monitor 都由聚合查询接口提供

详见根目录 `TODO.md`。
