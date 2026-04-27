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

- Controller / Interfaces：HTTP 入口与请求响应 DTO
- Service / Application：业务流程与用例编排
- Repository / Infrastructure：JdbcTemplate 查询、消息存储、上下文装配
- Domain：领域对象、状态、可见性策略、幂等键构造
- Config / Auth：安全配置、JWT、当前用户与角色守卫

### 2.2 包级模块

- `auth`：登录、JWT、当前用户、角色守卫
- `manuscript`：稿件、版本、作者归属、PDF 上传
- `review`：轮次、分配、评审报告、Reviewer 论文阅读
- `decision`：主席决策入口
- `workflow`：Author / Reviewer / Chair / Admin 的聚合查询与 decision workbench 查询
- `analysis`：新的分析平台边界，替代旧的通用 agent-task 入口
- `audit` / `notification`：审计与通知
- `config` / `health` / `placeholder`：基础配置、健康检查和保留占位资源

### 2.3 `analysis` 包结构

- `application/`：`RequestScreeningAnalysisUseCase`、`RequestReviewerAssistUseCase`、`RequestConflictAnalysisUseCase`
- `domain/`：`AnalysisIntent`、`AnalysisProjection`、`AnalysisType`、`AnalysisStatus`、`AnalysisVisibilityPolicy` 等
- `infrastructure/`：intent / projection / outbox / inbox repository 与事件消费、上下文查询
- `interfaces/`：`AnalysisController`、`AnalysisDtos`

### 2.4 代表性文件

- `apps/api/src/main/java/com/example/review/config/SecurityConfig.java`
- `apps/api/src/main/java/com/example/review/manuscript/ManuscriptService.java`
- `apps/api/src/main/java/com/example/review/review/ReviewWorkflowService.java`
- `apps/api/src/main/java/com/example/review/workflow/DecisionWorkbenchQueryService.java`
- `apps/api/src/main/java/com/example/review/analysis/interfaces/AnalysisController.java`

## 3. 前端结构（apps/web）

### 3.1 核心层次

- `views/`：按角色拆分的页面组件
- `layouts/`：应用外壳与导航骨架
- `router/`：路由与角色元数据
- `stores/`：登录态与用户上下文
- `lib/`：API 调用、格式化、通用工具
- `tests/`：登录、工作流、分析投影视图相关测试

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
- `apps/web/src/tests/agent-projection.spec.ts`

## 4. Agent 服务结构（services/agent）

### 4.1 当前入口与平台层

- `app/main.py`：组装 `AgentPlatformRuntime`
- `app/agent_platform/config.py`：环境配置与持久化选择
- `app/agent_platform/domain.py`：执行平台领域对象
- `app/agent_platform/state_machine.py`：执行状态机
- `app/agent_platform/repositories.py`：内存 / Oracle 执行仓储
- `app/agent_platform/consumer.py` / `publisher.py` / `outbox.py`：消息消费与发布
- `app/agent_platform/runtime.py`：平台运行时主流程

### 4.2 处理器与工作流层

- `app/agent_platform/handlers/`：`reviewer_assist`、`conflict_analysis`、`screening` 处理器
- `app/workflows/`：分析工作流与 schema
- `app/pdf_tools.py`：PDF 解析与页面处理
- `app/redaction.py`：双盲脱敏
- `app/models.py`：共享模型定义

### 4.3 已被替代的旧结构

- 旧的任务缓存模块已移除
- 旧的任务路由模块已移除
- 当前公开 HTTP 入口以应用健康检查和运行时装配为主，不再使用通用任务 REST 端点作为主路径

## 5. 数据库结构（database/oracle）

- `001_init.sql`：业务基础表
- `002_seed_roles.sql`：角色种子
- `003_indexes.sql`：业务索引
- `004_procedures.sql`：存储过程
- `005_triggers.sql`：基础触发器
- `006_seed_demo_users.sql`：演示用户
- `007_seed_demo_workflow.sql`：演示流程数据
- `008_agent_platform_refactor.sql`：分析意图 / 投影、执行作业、outbox / inbox 结构
- `009_execution_job_attempt_count.sql`：执行作业尝试计数补充迁移
- `verify_schema.sql`：对象校验

## 6. 工具脚本（scripts）

- `dev-up.sh`：本地启动入口，带 Oracle 启动与 demo seed 回放
- `test-all.sh`：仓库级验证入口
- `rabbitmq-up.sh`：本地 RabbitMQ 启动与健康检查
- `demo-seed.sh` / `oracle-demo-seed.sh`：演示数据准备
- `oracle-schema-apply.sh` / `oracle-up.sh`：Oracle 初始化与 schema 应用
