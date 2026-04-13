# PROJECT GUIDE（总览）

本文档是项目文档入口，帮助新成员快速定位信息。

## 1. 项目一句话

PaperTide Review（文澜审稿）是一个面向课程项目的智能论文评审系统，遵循“Agent 辅助，人类决策”的原则。

## 2. 阅读顺序（推荐）

1. `docs/ARCHITECTURE.md`：系统架构与服务边界
2. `docs/CODE_STRUCTURE.md`：当前代码结构与关键模块
3. `docs/DESIGN_STRUCTURE.md`：领域模型、权限、状态机设计
4. `docs/WORKFLOW.md`：角色视角流程与接口入口
5. `docs/TESTING.md`：测试策略、命令与质量门槛
6. `docs/TEST_RESULTS_2026-04-13.md`：当前测试结果快照

补充文档：

- 根目录 `README.md`：环境与启动
- 根目录 `CONTRIBUTING.md`：协作规范
- 根目录 `TODO.md`：缺陷与优化清单

## 3. 关键事实

- 代码仓结构：`apps/api` + `apps/web` + `services/agent` + `database/oracle`
- 技术栈：Spring Boot + Vue + FastAPI + Oracle
- 统一验证入口：`bash scripts/test-all.sh`

## 4. 当前状态

- 主业务流程可运行
- Agent 集成已上线并可查询分析结果
- 自动化测试在当前快照中通过（详见测试结果文档）
- 主要已知缺陷已整理为可执行待办（`TODO.md`）
