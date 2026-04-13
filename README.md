# PaperTide Review (文澜审稿)

智能论文评审系统（课程项目）。系统支持论文投稿、双盲评审、主席决策与 Agent 辅助分析，遵循 **Agent 辅助、人类决策** 原则。

## 核心能力

- 多角色权限：`AUTHOR`、`REVIEWER`、`CHAIR`、`ADMIN`
- 论文全流程：投稿、分配、评审、冲突汇总、主席终审
- 双盲约束：Agent 结果支持原始/脱敏视图
- 异步 Agent 集成：任务创建、轮询、结果回填

## 技术栈

- 后端：Java 21, Spring Boot 3.3, Spring Security, JWT, Oracle
- 前端：Vue 3, Vite, TypeScript, Element Plus
- Agent：Python 3.11+, FastAPI, LangGraph

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

Oracle SQL 需按顺序执行：

1. `database/oracle/001_init.sql`
2. `database/oracle/002_seed_roles.sql`
3. `database/oracle/003_indexes.sql`
4. `database/oracle/004_procedures.sql`
5. `database/oracle/005_triggers.sql`
6. `database/oracle/006_seed_demo_users.sql`
7. `database/oracle/007_seed_demo_workflow.sql`
8. `database/oracle/verify_schema.sql`

## 贡献指南

见 `CONTRIBUTING.md`。

## 已知问题与改进计划

见 `TODO.md`。
