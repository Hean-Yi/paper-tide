# PaperTide Review

智能论文评审系统（课程项目）。系统支持论文投稿、双盲评审、主席决策与 Agent 辅助分析，遵循 **Agent 辅助、人类决策** 原则。

## 核心能力

- 多角色权限：`AUTHOR`、`REVIEWER`、`CHAIR`、`ADMIN`
- 会议流程：公开注册、CFP 发布、会议投稿、审稿人池、bidding、引导分配
- 论文全流程：投稿、分配草稿、评审、冲突汇总、主席终审
- 双盲约束：Agent 结果支持原始/脱敏视图
- 异步 Agent 集成：业务 intent、RabbitMQ/outbox、执行作业、结果投影

## 技术栈

- 后端：Java 21, Spring Boot 3.3, Spring Security, JWT, Oracle
- 前端：Vue 3, Vite, TypeScript, Element Plus
- Agent：Python 3.11+, FastAPI, RabbitMQ worker, deterministic offline provider fallback

## 仓库结构

```text
apps/
  api/        # Spring Boot 主系统
  web/        # Vue 前端
services/
  agent/      # FastAPI Agent 服务
database/
  oracle/     # Oracle schema/seed/procedure/trigger
scripts/      # 一键启动与一键验证脚本
docs/         # 项目文档
```

## 快速开始

### 1. 环境准备

- Java 21+
- Maven 3.9+
- Node.js 20+
- Python 3.11+
- Oracle（本地容器或远端实例）
- RabbitMQ（本地容器，消息驱动 Agent 路径需要）

### 2. 一键启动（自动跳过缺失运行时）

```bash
bash scripts/dev-up.sh
```

### 3. 一键验证

```bash
bash scripts/test-all.sh
```

## 分服务开发命令

### API

```bash
cd apps/api
mvn spring-boot:run
mvn test
```

### Agent

```bash
cd services/agent
python3 -m uvicorn app.main:app --reload --port 8001
python3 -m pytest tests/
```

### Web

```bash
cd apps/web
npm run dev
npm run test -- --run
npm run typecheck
npm run build
```

## 数据库初始化

推荐使用脚本按当前迁移顺序初始化并校验：

```bash
bash scripts/oracle-schema-apply.sh
```

当前迁移已经覆盖注册审批、会议/CFP、会议范围投稿、reviewer pool/bidding、assignment draft、analysis intent/execution 平台和 `REVIEWER_ASSIGNMENT_ASSIST`。直接手动执行 SQL 时必须保持 `database/oracle/*.sql` 的编号顺序，最后运行 `database/oracle/verify_schema.sql`。

## 环境变量

环境变量模板见 `.env.example`，详细说明见 `docs/ENVIRONMENT.md`。默认不配置 LLM provider 时，Agent 使用 deterministic offline fallback，方便本地验证和演示不消耗外部模型额度。

## 贡献指南

见 `CONTRIBUTING.md`。

## 已知问题与改进计划

见 `TODO.md`。

## 项目文档

- 总览：`docs/PROJECT_GUIDE.md`
- 架构：`docs/ARCHITECTURE.md`
- 代码结构：`docs/CODE_STRUCTURE.md`
- 设计结构：`docs/DESIGN_STRUCTURE.md`
- Workflow：`docs/WORKFLOW.md`
- 测试说明：`docs/TESTING.md`
- 环境配置：`docs/ENVIRONMENT.md`
- 测试快照：`docs/TEST_RESULTS_2026-04-13.md`
- 演示文档包：`docs/demo/README.md`
