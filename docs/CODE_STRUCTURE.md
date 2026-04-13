# 当前代码结构

## 1. 仓库级结构

```text
apps/
  api/
  web/
services/
  agent/
database/
  oracle/
scripts/
docs/
```

## 2. 后端结构（apps/api）

### 2.1 核心分层

- Controller：HTTP 路由入口
- Service：业务规则与流程编排
- Repository：JDBC 查询与数据映射
- Config/Auth：安全配置、JWT、当前用户上下文

### 2.2 包级模块

- `auth`：登录、JWT、认证仓储
- `manuscript`：稿件与版本
- `review`：轮次、分配、冲突检查、评审报告
- `decision`：主席工作台与决策
- `workflow`：聚合查询接口（前端页面数据）
- `agent`：Agent 集成、轮询、任务与结果
- `audit` / `notification`：审计与通知

### 2.3 代表性文件

- `apps/api/src/main/java/com/example/review/config/SecurityConfig.java`
- `apps/api/src/main/java/com/example/review/manuscript/ManuscriptService.java`
- `apps/api/src/main/java/com/example/review/review/ReviewWorkflowService.java`
- `apps/api/src/main/java/com/example/review/decision/DecisionService.java`
- `apps/api/src/main/java/com/example/review/agent/AgentIntegrationService.java`

## 3. 前端结构（apps/web）

### 3.1 核心层次

- `views/`：页面组件（按角色分组）
- `layouts/`：外壳布局
- `router/`：路由与权限元信息
- `stores/`：登录态与角色状态
- `lib/`：API 调用与格式化函数

### 3.2 角色页面

- Author：`views/author/*`
- Reviewer：`views/reviewer/*`
- Chair：`views/chair/*`
- Admin：`views/admin/*`

### 3.3 代表性文件

- `apps/web/src/router/index.ts`
- `apps/web/src/stores/auth.ts`
- `apps/web/src/lib/workflow-api.ts`
- `apps/web/src/views/chair/DecisionWorkbenchView.vue`

## 4. Agent 服务结构（services/agent）

### 4.1 核心层次

- `app/routes`：HTTP 路由
- `app/workflows`：LangGraph 工作流实现
- `app/task_store.py`：任务存储与缓存复用
- `app/pdf_tools.py`：PDF 提取与标准化
- `app/redaction.py`：双盲脱敏

### 4.2 代表性文件

- `services/agent/app/routes/tasks.py`
- `services/agent/app/workflows/router.py`
- `services/agent/app/workflows/schemas.py`
- `services/agent/app/task_store.py`

## 5. 数据库结构（database/oracle）

- `001_init.sql`：核心表定义
- `002_seed_roles.sql`：角色种子
- `003_indexes.sql`：索引
- `004_procedures.sql`：过程
- `005_triggers.sql`：触发器
- `006_seed_demo_users.sql`：演示用户
- `007_seed_demo_workflow.sql`：演示流程数据
- `verify_schema.sql`：对象校验

## 6. 工具脚本（scripts）

- `dev-up.sh`：本地多服务启动
- `test-all.sh`：全量验证入口
- `oracle-*.sh`：Oracle 初始化/种子脚本
