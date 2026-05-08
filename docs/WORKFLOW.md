# 当前 Workflow

## 1. 角色视角总览

### Author

1. 公开注册后直接激活账号
2. 登录后浏览公开 CFP
3. 选择会议创建稿件与上传版本 PDF
3. 提交版本进入评审流程
4. 跟踪状态与决策结果

### Reviewer

1. 公开注册后等待 Admin 审批进入平台 reviewer pool
2. 在会议 bidding 窗口提交偏好或声明冲突
3. 登录后查看被分配任务
4. 接受/拒绝任务
5. 填写并提交评审报告

### Chair

1. 公开注册 Organizer，由 Admin 审批后获得现有 `CHAIR` 角色
2. 创建会议 CFP 并提交 Admin 审批
3. 管理 reviewer pool
4. 查看 screening queue
5. 创建评审轮次，生成 assignment draft，并确认 Reviewer 分配
6. 发起 screening analysis、reviewer assignment assist 或 conflict analysis
7. 在 decision workbench 做终审决策

### Admin

1. 审批 Reviewer / Organizer 角色申请
2. 审批会议 CFP
3. 监控 Agent intent、projection 与执行状态
4. 查看系统管理相关能力

## 2. 端到端主流程

```text
Author selects CFP + submits manuscript
  -> Chair builds reviewer pool + opens bidding
  -> Reviewer submits bid/conflict signal
  -> Chair starts screening / round
  -> Chair generates assignment drafts
  -> Chair optionally requests reviewer assignment assist
  -> Chair confirms reviewers
  -> Reviewer submits reports
  -> Chair checks conflict + analysis insights
  -> Chair records decision
  -> Manuscript status updated
```

## 3. API Workflow 入口（摘要）

- Auth
  - `POST /api/auth/login`
  - `POST /api/auth/register`
  - `POST /api/auth/verify-email`（兼容旧验证链接；新注册默认不要求邮箱验证）

- Conference / registration admin
  - `GET /api/conferences/cfp`
  - `POST /api/chair/conferences`
  - `POST /api/chair/conferences/{conferenceId}/submit-approval`
  - `GET /api/admin/conferences/pending`
  - `POST /api/admin/conferences/{conferenceId}/approve`
  - `GET /api/admin/role-applications`
  - `POST /api/admin/role-applications/{applicationId}/approve`

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
  - `POST /api/chair/conferences/{conferenceId}/reviewers`
  - `GET /api/reviewer/conferences/{conferenceId}/bids/open`
  - `POST /api/reviewer/conferences/{conferenceId}/bids`
  - `POST /api/review-rounds/{roundId}/assignment-drafts/generate`
  - `GET /api/review-rounds/{roundId}/assignment-drafts`
  - `POST /api/review-rounds/{roundId}/assignment-drafts/confirm`
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
  - `POST /api/review-rounds/{roundId}/assignment-assist`
  - `GET /api/review-rounds/{roundId}/assignment-assist`
  - `POST /api/review-rounds/{roundId}/conflict-analysis`

## 4. 分析子流程

当前仓库里的分析请求不再走通用任务 REST 模型，而是按业务锚点直接发起 intent。

### 4.1 Screening

`manuscript version -> analysis intent -> execution job -> projection`

### 4.2 Reviewer Assist

`assignment -> analysis intent -> execution job -> reviewer-scoped projection`

### 4.3 Conflict Analysis

`review round -> analysis intent -> execution job -> chair-facing projection`

### 4.4 Reviewer Assignment Assist

`review round -> assignment drafts -> analysis intent -> execution job -> chair-facing projection`

## 5. 执行侧工作流阶段

执行平台内部仍会调用工作流组件，典型阶段是：

- `understand`
- `analyze`
- `validate`
- `redact`

不同分析类型复用这些阶段，但由 `agent_platform/handlers/*` 选择具体处理器和上下文装配方式。

## 6. 当前 Workflow 说明

- Screening、Reviewer Assist、Conflict Analysis 都是异步请求，控制器返回 `202 Accepted`
- Reviewer Assignment Assist 也是异步请求，输入只包含一篇稿件/版本和当前 assignment draft 候选
- Reviewer Assist 已有独立读取端点，便于 Reviewer 页面轮询或刷新当前 assignment 的分析状态
- Chair 的 screening queue 和 decision workbench、Admin 的 analysis monitor 都由聚合查询接口提供

详见根目录 `TODO.md`。
