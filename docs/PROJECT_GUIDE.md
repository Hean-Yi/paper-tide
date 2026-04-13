# 项目文档（Project Guide）

## 1. 项目定位

PaperTide Review 是一个面向课程项目的智能论文评审系统。

- 主流程：投稿 -> 筛选/分配 -> 评审 -> 主席决策
- Agent 职责：提供分析建议，不替代人类评审结论
- 核心原则：双盲合规、权限隔离、可追踪

## 2. 系统架构

系统由三个服务组成：

- `apps/api`：主业务系统（Spring Boot）
- `apps/web`：角色化前端（Vue + Element Plus）
- `services/agent`：异步分析服务（FastAPI + LangGraph）

数据统一由 Oracle 承载，Agent 服务不直接访问数据库。

## 3. 角色与权限

- `AUTHOR`：提交/查看自己论文与版本
- `REVIEWER`：查看被分配稿件、提交评审
- `CHAIR`：管理轮次、分配审稿、发起决策
- `ADMIN`：系统管理与运维能力

## 4. 关键目录

```text
apps/api/src/main/java      # API 业务实现
apps/api/src/test/java      # API 测试
apps/web/src                # 前端页面与路由
services/agent/app          # Agent 路由、模型、工作流
services/agent/tests        # Agent 测试
database/oracle             # Oracle DDL/DML/触发器/过程
scripts                     # 启停、测试、数据库脚本
```

## 5. 本地开发流程

1. 按 README 准备 Java/Node/Python/Oracle 环境
2. 初始化数据库 schema 与种子数据
3. 运行 `bash scripts/dev-up.sh` 启动服务
4. 运行 `bash scripts/test-all.sh` 完整验证

## 6. 质量门槛

- 后端改动：至少通过受影响模块测试
- 前端改动：必须通过 `test + typecheck + build`
- Agent 改动：必须通过 pytest
- 涉及权限/状态机改动：建议补充回归测试

## 7. 协作建议

- 一次 PR 聚焦一个主题（功能、修复或重构）
- 对重复逻辑优先抽取共享组件/服务
- 对高风险问题（状态机、安全策略）优先修复

## 8. 已知缺陷

当前问题清单与优先级见仓库根目录 `TODO.md`。
