# API Cheat Sheet

## 认证

- `POST /api/auth/login`
  - 用途：获取 JWT

## 稿件与版本

- `POST /api/manuscripts`
  - 用途：新建稿件
- `GET /api/manuscripts`
  - 用途：查询稿件列表
- `GET /api/manuscripts/{id}`
  - 用途：查询稿件详情
- `POST /api/manuscripts/{id}/versions`
  - 用途：新增版本
- `GET /api/manuscripts/{id}/versions`
  - 用途：查询版本列表
- `POST /api/manuscripts/{id}/versions/{versionId}/pdf`
  - 用途：上传 PDF
- `GET /api/manuscripts/{id}/versions/{versionId}/pdf`
  - 用途：下载 PDF
- `POST /api/manuscripts/{id}/versions/{versionId}/submit`
  - 用途：提交版本
- `POST /api/manuscripts/{id}/versions/{versionId}/start-screening`
  - 用途：进入筛选（当前实现中由 Chair 触发）

## 评审流程

- `POST /api/review-rounds`
  - 用途：创建评审轮次
- `POST /api/review-rounds/{roundId}/assignments`
  - 用途：指派 reviewer
- `GET /api/review-rounds/{roundId}/conflict-checks`
  - 用途：查询冲突检查
- `POST /api/review-assignments/{assignmentId}/accept`
  - 用途：接受评审任务
- `POST /api/review-assignments/{assignmentId}/decline`
  - 用途：拒绝评审任务
- `POST /api/review-assignments/{assignmentId}/review-report`
  - 用途：提交评审报告

## 决策与工作台

- `GET /api/decisions`
  - 用途：查询决策列表/工作台数据
- `POST /api/decisions`
  - 用途：提交终审决策

## Agent 集成

- `POST /api/manuscripts/{manuscriptId}/versions/{versionId}/agent-tasks`
  - 用途：创建分析任务
- `POST /api/review-rounds/{roundId}/conflict-analysis`
  - 用途：创建冲突分析任务
- `GET /api/manuscripts/{manuscriptId}/versions/{versionId}/agent-results`
  - 用途：查询分析结果
- `GET /api/agent-tasks`
  - 用途：查询任务监控列表（Admin）

## Workflow 查询接口

- `GET /api/review-assignments`
- `GET /api/review-assignments/{assignmentId}`
- `GET /api/chair/screening-queue`
- `GET /api/chair/decision-workbench`

## 安全说明

- 除登录与健康检查外，默认需要 JWT
- 建议请求头携带：`Authorization: Bearer <token>`
