# TODO - Module Review Backlog

本清单按模块整理当前代码审查结论，目标是把“需要修什么”直接落成可执行待办，而不是停留在泛化意见。优先级含义：

- `[P0]`：影响真实交付闭环、安全边界或核心运行可信度，需优先修复
- `[P1]`：明显工程债、边界模糊或可维护性风险，建议尽快修复
- `[P2]`：中期重构、性能和产品化增强

## Cross-cutting

- [ ] `[P0]` 完成新的 analysis 平台运行闭环：当前 API 侧只会写 `ANALYSIS_OUTBOX`，agent 侧只组装 runtime 和 `/health`，但仓库里还没有真正的 RabbitMQ 发布/消费生命周期接线、pending outbox 派发器、completion event 回流调度与端到端集成验证。
- [ ] `[P0]` 统一当前架构叙事：`README.md`、`docs/ARCHITECTURE.md`、`docs/CODE_STRUCTURE.md`、`docs/TESTING.md` 仍混合描述旧的 `/agent/tasks + HTTP polling` 路径与新的 intent/projection/message-driven 路径，需要选定唯一现行实现并同步文档。
- [ ] `[P1]` 为仓库建立统一错误契约：后端输出稳定 JSON 错误结构（`status`、`code`、`message`、`traceId`），前端不再依赖 `statusText` 和散落的字符串分支。
- [ ] `[P1]` 增加最小可观测性基线：请求/消息 `traceId`、结构化日志、关键异步链路日志字段、健康检查分层（readiness/liveness），避免 message-driven 路径上线后只能靠手工查表排障。
- [ ] `[P1]` 增加仓库级 CI：至少覆盖 `apps/api`、`apps/web`、`services/agent` 的自动测试、类型检查和构建，避免当前只能依赖本地脚本。
- [ ] `[P1]` 补齐环境治理：新增 `.env.example` 或 `docs/ENVIRONMENT.md`，集中说明 Oracle、RabbitMQ、JWT、API/agent 内部 key、OpenAI/OpenRouter、端口和前端代理配置。

## apps/api

- [ ] `[P0]` 补上 analysis 消息链路的真实运行入口：当前 `AnalysisOutboxPublisher` 只落库，`AnalysisEventConsumer` 只有消费逻辑却没有真正 listener/dispatcher；需要把 broker publishing 和 event consuming 从“代码片段”补成“可运行基础设施”。
- [ ] `[P1]` 收紧 service 边界：`ManuscriptService`、`ReviewWorkflowService`、`DecisionService` 等大量直接抛 `ResponseStatusException`，把 HTTP 语义带入业务层；需要引入应用/领域异常和统一映射层。
- [ ] `[P1]` 收敛查询层 N+1：`WorkflowQueryService.listDecisionWorkbench(...)` 先查 round，再逐条补 assignment、intent、projection，属于典型聚合读模型 N+1，应改为面向页面的一次性批量查询。
- [ ] `[P1]` 把 service 中的 JDBC 细节进一步下沉到 repository：当前仍有直接 `JdbcTemplate.update(...)`、`SELECT ... FOR UPDATE` 和临时 row shape 留在 service 层，导致模块职责不够纯。
- [ ] `[P1]` 收敛聚合查询文件体积和职责：`WorkflowQueryService.java` 已成为 400+ 行页面读模型拼装层，应拆分 reviewer/chair/admin 查询或引入专门 read-model repository。
- [ ] `[P1]` 提取重复 RowMapper / SQL 片段为常量或小型 mapper，减少 `WorkflowQueryService`、`ReviewerPaperService`、分析仓储中的重复查询样板。
- [ ] `[P1]` 为 admin monitor 增加分页、筛选和状态过滤；当前固定返回最新 50 条，只适合 demo，不适合真实治理页面。
- [ ] `[P2]` 把核心 workflow 状态迁移为显式 `enum + transition table`，替换各 service 中散落的字符串集合判断。
- [ ] `[P2]` 梳理通知/审计边界：当前核心事务里仍直接调用通知服务并吞异常，后续应统一为事务事件或 outbox 派发模式。

## apps/web

- [ ] `[P1]` 拆分 `src/lib/workflow-api.ts`：当前一个文件承载 author/reviewer/chair/admin 全部 API shape 和调用，已经接近单点耦合，应按领域或 actor 拆分。
- [ ] `[P1]` 收敛 `DecisionWorkbenchView.vue`：页面信息密度过高、表格展开层级深、动作触发后依赖整页刷新，且 `conflict(...)` 未接入统一 loading/error 包装，需拆成列表页 + 详情页或抽出子组件。
- [ ] `[P1]` 统一前端异步交互：`ReviewerAgentPanel.vue` 仍手写 `loading/running/error`，未复用 `useAsyncAction` / `useApiError`，错误文案也没有区分 `401/403/409/502/503` 的操作建议。
- [ ] `[P1]` 为 reviewer/chair/admin 的高风险动作补确认步骤，尤其是会触发不可逆 workflow 迁移或外部分析成本的操作。
- [ ] `[P1]` 优化 auth 生命周期：当前 `auth.ts` 主要依赖本地 JWT 解码恢复会话，缺少统一的 401 失效处理和路由级重新登录策略。
- [ ] `[P2]` 继续抽取通用表单/对话框模式，例如 `useDialog<T>(submitFn)`，减少 Element Plus 表单在多个页面里重复样板。
- [ ] `[P2]` 提升 admin monitor 和 reviewer assist 的交互完成度：增加自动刷新策略、最近更新时间、空态/失败态引导和最小筛选能力。
- [ ] `[P2]` 评估 `SecurePaperReader` 的大文件策略：长论文按页 PNG 渲染的体积、首屏时间、缓存和 WebP 替代方案需要真实样本验证。

## services/agent

- [ ] `[P0]` 将 agent 平台从“组装好的对象图”补成“运行中的服务”：`create_app()` 当前只暴露 `/health`，没有 broker consumer、outbox publisher worker、startup/shutdown 生命周期管理，也没有从消息入口真正驱动 `execute_requested_job(...)`。
- [ ] `[P0]` 明确唯一执行栈：当前同时保留 `agent_platform/handlers/*` 和旧 `app/workflows/*` LangGraph 路径，测试也同时覆盖两套模型；需要确定保留哪一套，避免长期双轨。
- [ ] `[P1]` 为 provider 执行层建立真实边界：`ProviderExecutor` 目前主要是 deterministic stub，后续要把真实模型调用、超时、错误分类、幂等日志、成本控制和 provider 配置隔离到单独适配层。
- [ ] `[P1]` 补齐 execution job 生命周期数据：除 `ATTEMPT_COUNT` 外，还需要评估是否应持久化最近错误分类、最后尝试时间、完成时间、发布失败原因等治理字段。
- [ ] `[P1]` 为 message-driven 路径增加 focused 集成测试：证明“requested message -> execution job -> completed event -> projection ready”能够在真实 broker/DB 条件下跑通，而不仅是仓储和纯内存单测。
- [ ] `[P2]` 继续清理迁移遗留：删除不再使用的旧 route/task API、旧设计注释和已被 handler 方案替代的 workflow 入口，降低认知负担。

## database/oracle

- [ ] `[P0]` 明确 legacy `AGENT_*` 表与新 `ANALYSIS_*` / `EXECUTION_*` 表的并存策略：当前 schema、seed、trigger、verify 仍同时维护两套 agent 数据模型，容易让后续开发误判真实来源；需要确定淘汰计划或显式标记 legacy only。
- [ ] `[P1]` 为新的 message-driven 表继续补治理字段和查询索引，只要运行面需要按状态、重试、失败原因或时间窗口排障，就要同步落到 schema 和 `verify_schema.sql`。
- [ ] `[P1]` 收敛 demo seed 对 legacy agent 数据的依赖，避免真实页面已经切到新读模型，但 seed 和演示脚本仍把旧表当权威来源。
- [ ] `[P1]` 为 schema 演进补一份迁移说明，明确从 first-generation agent tables 迁移到 new intent/execution tables 的顺序、兼容边界和清理条件。
- [ ] `[P2]` 评估把页面型聚合查询沉淀为更明确的 read model 或 view，减轻 API 端大量手写 join/count 子查询的维护成本。

## scripts / docs / devops

- [ ] `[P0]` 修正 `scripts/test-all.sh` 的可信度：当前 agent 只跑 `test_health.py`，无法代表 analysis runtime 是否可用；统一验证入口必须覆盖真实关键路径。
- [ ] `[P1]` 为 `dev-up.sh`、`test-all.sh`、`README.md`、`docs/TESTING.md` 对齐当前架构阶段，避免脚本与文档继续向开发者暗示旧的 HTTP task 模型已经是现行路径。
- [ ] `[P1]` 增加最小部署资产：至少补齐 CI workflow、容器化或运行拓扑说明；当前更像“本地课程项目 bootstrap”，还不是可重复部署的落地工程。
- [ ] `[P1]` 为 RabbitMQ / Oracle / agent runtime 的联调失败增加更聚焦的诊断输出与操作指引，减少脚本失败后只能靠读源码排障。
- [ ] `[P2]` 把 docs 从“设计历史 + 当前说明混放”改成“现行实现文档 + 历史设计归档”结构，降低新开发者误读成本。

## Productization Gaps

- [ ] `[P1]` 定义最小运维面：失败重驱、死信处理、手工补偿、消息积压观察和分析任务审计查询。
- [ ] `[P1]` 明确安全基线：环境密钥注入方式、JWT secret 管理、内部 broker/consumer 信任边界、敏感日志脱敏策略。
- [ ] `[P2]` 明确容量与性能基线：大 PDF 渲染、批量 round 查询、analysis projection 列表、message backlog 的容量假设与压测方式。
