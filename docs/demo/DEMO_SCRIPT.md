# Demo Script（15-20 分钟）

## 0. 开场（1 分钟）

- 项目名称：PaperTide Review（文澜审稿）
- 定位：智能论文评审系统，强调“Agent 辅助，人类决策”
- 架构：Web + API + Agent + Oracle

## 1. 系统架构说明（2 分钟）

- Web：Vue 3 + Element Plus
- API：Spring Boot + JWT + Oracle
- Agent：FastAPI + LangGraph
- 关键边界：Agent 不直连数据库，由 API 编排

## 2. Author 路径演示（3 分钟）

1. 使用 Author 账号登录
2. 创建稿件并上传版本 PDF
3. 提交版本
4. 展示稿件状态变化

讲解点：

- 投稿数据和版本数据的分离
- 双盲流程入口已建立

## 3. Chair 路径演示（4 分钟）

1. 切换 Chair 账号
2. 打开 screening queue
3. 创建 review round
4. 指派 reviewer
5. 触发 Agent 分析任务

讲解点：

- 轮次和分配是流程控制核心
- Agent 分析是异步，不阻塞主流程

## 4. Reviewer 路径演示（3 分钟）

1. Reviewer 登录
2. 查看 assignment 列表
3. 接受任务
4. 提交评审报告

讲解点：

- Reviewer 只能看到被分配稿件
- 评审数据为后续冲突分析提供输入

## 5. Chair 决策演示（5 分钟）

1. 回到 Chair Decision Workbench
2. 查看 review 汇总和 Agent 结果
3. 执行最终决策
4. 展示稿件状态更新

讲解点：

- 决策链路是“人类最终裁决”
- Agent 结果仅提供参考，保留人工判断

## 6. 质量与问题说明（2 分钟）

- 展示 `bash scripts/test-all.sh` 的通过结果
- 展示 `TODO.md` 中 P0/P1 问题与优化计划

## 7. 结束语（1 分钟）

- 当前系统已具备端到端可演示能力
- 后续重点：状态机枚举化、前端交互优化、Agent 约束增强
