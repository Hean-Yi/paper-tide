# 系统架构

## 1. 总体架构

PaperTide Review 采用三服务 + 单数据库的解耦架构。

- `apps/api`：主业务系统（认证、投稿、评审流程、主席决策、Agent 集成）
- `apps/web`：角色化前端（Author/Reviewer/Chair/Admin）
- `services/agent`：异步分析服务（任务执行、结果生成、脱敏）
- `database/oracle`：统一持久化层（业务表、审计表、触发器、过程）

## 2. 运行拓扑

```text
[Browser]
    |
    v
[Vue Web: apps/web] --JWT--> [Spring API: apps/api] --JDBC--> [Oracle]
                                   |
                                   | HTTP (X-Agent-Api-Key)
                                   v
                           [FastAPI Agent: services/agent]
```

关键边界：

- 前端永远通过 API 访问数据，不直连 Oracle
- Agent 服务不直连 Oracle，由 API 负责业务数据编排和结果落库
- 主系统负责权限、状态机与事务一致性

## 3. 服务职责边界

### 3.1 API（Spring Boot）

- 账号认证与 JWT 鉴权
- 投稿与版本管理（PDF 上传/下载、提交）
- 评审轮次、分配、冲突检查、评审报告
- 主席决策与工作台查询
- 调用 Agent 创建任务并回写分析结果

### 3.2 Web（Vue 3）

- 登录态管理与路由守卫
- 按角色展示 workflow 页面
- 组织用户输入并调用 API
- 呈现分析结果、冲突摘要与审稿状态

### 3.3 Agent（FastAPI + LangGraph）

- 接收任务（JSON 或 multipart + PDF）
- 任务路由到对应工作流：
  - `SCREENING_ANALYSIS`
  - `REVIEW_ASSIST_ANALYSIS`
  - `DECISION_CONFLICT_ANALYSIS`
- 输出 raw + redacted 结构化结果

## 4. 数据流与一致性

### 4.1 投稿与评审主链路

1. Author 提交稿件与版本
2. Chair 发起评审轮次与分配
3. Reviewer 提交评审报告
4. Chair 汇总冲突并给出最终决策
5. API 原子更新业务状态并写入审计/通知

### 4.2 Agent 异步链路

1. API 从 Oracle 读取稿件 PDF 与上下文
2. API 调用 Agent 创建任务
3. API 轮询任务状态并获取结果
4. API 将 raw/redacted 结果持久化到 Oracle

## 5. 安全架构

- JWT 无状态会话
- Spring Security 路径级权限控制
- Agent 内部接口基于 `X-Agent-Api-Key` 保护
- 双盲结果通过 redacted 视图约束展示范围

## 6. 当前架构优点

- 组件边界清晰，演进空间好
- Agent 故障不阻塞主业务事务
- 前后端角色边界明确

## 7. 当前架构已知风险

- `SecurityConfig` 仍存在 `anyRequest().permitAll()` 风险
- Agent 任务缓存当前会复用 `FAILED` 状态（应仅复用 `SUCCESS`）
- 前端当前路由未懒加载，主包体积偏大

详细问题见根目录 `TODO.md`。
