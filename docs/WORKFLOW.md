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
3. 触发 Agent 分析（筛选/冲突）
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
  -> Chair checks conflict + agent insights
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
  - `POST /api/review-rounds`
  - `POST /api/review-rounds/{roundId}/assignments`
  - `POST /api/review-assignments/{assignmentId}/accept`
  - `POST /api/review-assignments/{assignmentId}/review-report`

- Decision
  - `GET /api/decisions`
  - `POST /api/decisions`

- Agent
  - `POST /api/manuscripts/{manuscriptId}/versions/{versionId}/agent-tasks`
  - `POST /api/review-rounds/{roundId}/conflict-analysis`
  - `GET /api/manuscripts/{manuscriptId}/versions/{versionId}/agent-results`

## 4. Agent 子流程

### 4.1 Screening Analysis

`understand -> analyze -> validate -> redact`

### 4.2 Review Assist Analysis

`understand -> analyze -> validate -> redact`

### 4.3 Conflict Analysis

`analyze -> validate -> redact`

## 5. 当前 Workflow 缺口

- Agent 工作流轮次上限尚未在代码中强约束
- 部分前端交互反馈（loading、confirm、局部刷新）仍待补齐
- Decision Workbench 页面信息密度偏高，建议拆分

详见根目录 `TODO.md`。
