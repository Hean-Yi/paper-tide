# TODO - Module Review Backlog

本清单按模块整理当前代码审查结论，目标是把“需要修什么”直接落成可执行待办，而不是停留在泛化意见。优先级含义：

- `[P0]`：影响真实交付闭环、安全边界或核心运行可信度，需优先修复
- `[P1]`：明显工程债、边界模糊或可维护性风险，建议尽快修复
- `[P2]`：中期重构、性能和产品化增强

## Implementation Landing Plan

- Wave 1 - shared execution foundations: close cross-cutting error contracts, trace IDs, environment docs, and verification scripts so later business-flow work has stable diagnostics.
- Wave 2 - analysis/runtime hardening: add real Oracle + RabbitMQ integration verification, keep exactly one Agent execution stack, and finish provider failure classification plus lifecycle governance fields.
- Wave 3 - API/Web maintainability: split oversized read/API modules, move JDBC details into repositories, standardize frontend async actions, and add admin monitor pagination/filtering.
- Wave 4 - real-deployment business P0: implement conference-scoped authorization, phase/deadline enforcement, COI/double-blind governance, and author-facing decision packages.
- Wave 5 - product operations: add reviewer discussion/meta-review, publication/camera-ready, communication, deployment, reporting, and data lifecycle capabilities.
- Wave 6 - configurable conference workflow: configurable submission/review/meta-review/camera-ready form read/render/save/submit paths, saved draft hydration, review revision history, and explicit rebuttal/camera-ready window enforcement have landed; deeper author-feedback threading, review quality/rating, and richer per-track phase policy remain future product depth.
- Wave 7 - chair-scale operations: backend foundation landed for paper tags, tag CSV preview-confirm semantics, paper role assignment such as `SHEPHERD`, and chair assignment-operations workbench read models; still needs saved search/formula filters, CSV export files, bulk decision/user/conflict/preference confirms, and deeper workbench tag/filter integration.
- Wave 8 - assignment and COI maturity: reviewer invitations, external reviewer delegation approve/reject, conflict graph rows, matching score import, bulk invitation/TPMS preview-confirm, advisory assignment proposals, proposal confirm-to-draft, override audit, and chair assignment operations UI/read model landed; still needs richer subject-area matching display, Agent proposal context enrichment, and first-class audited override UI.
- Wave 9 - publication and communication maturity: email template preview/test-send history, offline review template/preview/confirm, camera-ready metadata submit/accept/reject, publication metadata, proceedings preview/export metadata, and chair publication/communication workbench landed; still needs real file storage/download, proceedings file generation/download, DOI/index adapters, and a richer communication compose/reminder console.
- Wave 10 - production readiness and governance: add operator compensation actions, queue/dead-letter management, backups/restore drills, HA/disaster-recovery documentation, retention/anonymization/export/delete workflows, and capacity baselines.

## Cross-cutting

- [x] `[P0]` 完成新的 analysis 平台运行闭环：API pending outbox 派发器、completion listener、RabbitMQ 队列绑定、agent request consumer 生命周期和 completion outbox publisher 已补上；真实 Oracle + RabbitMQ 的 `scripts/analysis-e2e-smoke.sh` 已纳入 `scripts/test-all.sh` 的 `RUN_ANALYSIS_E2E_SMOKE=1` 路径。
- [x] `[P0]` 统一当前架构叙事：`README.md`、`docs/ARCHITECTURE.md`、`docs/CODE_STRUCTURE.md`、`docs/TESTING.md` 已对齐到当前 `analysis intent -> execution job -> projection` 的 message-driven 路径；旧 `/agent/tasks + HTTP polling` 不再作为现行实现说明。
- [x] `[P1]` 为仓库建立统一错误契约：后端应用错误和 Spring Security 401/403 入口输出稳定 JSON 结构（`status`、`code`、`message`、`traceId`），前端 `ApiError` 解析 `code`/`traceId` 并停止依赖 `statusText`。
- [x] `[P1]` 增加最小可观测性基线：请求级 `traceId` 响应头、MDC、错误体、消息 `traceId` 传播、Agent/API 异步链路日志字段、readiness/liveness 健康检查已经补齐。
- [x] `[P1]` 增加仓库级 CI：`.github/workflows/ci.yml` 覆盖 API、Agent、Web 的依赖安装与 `scripts/test-all.sh`，并启用真实 analysis e2e smoke。
- [x] `[P1]` 补齐环境治理：`.env.example` 和 `docs/ENVIRONMENT.md` 集中说明 Oracle、RabbitMQ、JWT、API/agent 内部 key、OpenAI/OpenRouter、端口和前端代理配置。

## apps/api

- [x] `[P0]` 补上 analysis 消息链路的真实运行入口：当前 `AnalysisOutboxPublisher` 只落库，`AnalysisEventConsumer` 只有消费逻辑却没有真正 listener/dispatcher；需要把 broker publishing 和 event consuming 从“代码片段”补成“可运行基础设施”。
- [ ] `[P1]` 收紧 service 边界：`ManuscriptService`、`ReviewWorkflowService`、`DecisionService` 等大量直接抛 `ResponseStatusException`，把 HTTP 语义带入业务层；需要引入应用/领域异常和统一映射层。
- [x] `[P1]` 收敛查询层 N+1：`WorkflowQueryService.listDecisionWorkbench(...)` 先查 round，再逐条补 assignment、intent、projection，属于典型聚合读模型 N+1，应改为面向页面的一次性批量查询。
- [x] `[P1]` 把 service 中的 JDBC 细节进一步下沉到 repository：`WorkflowQueryService` 的 reviewer/screening/admin 查询 SQL 已迁到 read repositories，`ReviewWorkflowService` 和 `DecisionService` 的 manuscript/round 状态更新与 round lock 查询已下沉到 owning repositories。
- [x] `[P1]` 收敛聚合查询文件体积和职责：`WorkflowQueryService.java` 已从页面 SQL 拼装层收敛为权限与编排层，reviewer、screening、admin monitor、decision workbench 查询均由专门 read repository/service 承担。
- [x] `[P1]` 提取重复 RowMapper / SQL 片段为常量或小型 mapper：wave3 已先把 `WorkflowQueryService` 中的重复 query/mapper 样板移入 `ReviewerAssignmentReadRepository`、`ScreeningQueueReadRepository`、`AdminAnalysisMonitorReadRepository`；`ReviewerPaperService` 和分析仓储的进一步 mapper 提取留给后续局部清理。
- [x] `[P1]` 为 admin monitor 增加分页、筛选和状态过滤；接口现在返回 page envelope，并支持 `page`、`size`、`analysisType`、`businessStatus`，前端 monitor 页面已补筛选和分页 UI。
- [ ] `[P2]` 把核心 workflow 状态迁移为显式 `enum + transition table`，替换各 service 中散落的字符串集合判断。
- [ ] `[P2]` 梳理通知/审计边界：当前核心事务里仍直接调用通知服务并吞异常，后续应统一为事务事件或 outbox 派发模式。

## apps/web

- [x] `[P1]` 拆分 `src/lib/workflow-api.ts`：API types 已移入 `workflow-types.ts`，调用按 actor 拆为 author/reviewer/chair/admin 模块，原 `workflow-api.ts` 保留为兼容 barrel。
- [x] `[P1]` 收敛 `DecisionWorkbenchView.vue`：本轮先补齐 `conflict(...)` 的统一 loading/error 包装；页面拆成列表页 + 详情页或更细子组件仍属于后续 UI 结构优化。
- [x] `[P1]` 统一前端异步交互：`ReviewerAgentPanel.vue` 已改用 `useAsyncAction` 管理 run/refresh 独立 pending key，并继续复用 `apiErrorMessage` 的状态码文案映射。
- [ ] `[P1]` 为 reviewer/chair/admin 的高风险动作补确认步骤，尤其是会触发不可逆 workflow 迁移或外部分析成本的操作。
- [ ] `[P1]` 优化 auth 生命周期：当前 `auth.ts` 主要依赖本地 JWT 解码恢复会话，缺少统一的 401 失效处理和路由级重新登录策略。
- [ ] `[P2]` 继续抽取通用表单/对话框模式，例如 `useDialog<T>(submitFn)`，减少 Element Plus 表单在多个页面里重复样板。
- [ ] `[P2]` 提升 admin monitor 和 reviewer assist 的交互完成度：增加自动刷新策略、最近更新时间、空态/失败态引导和最小筛选能力。
- [ ] `[P2]` 评估 `SecurePaperReader` 的大文件策略：长论文按页 PNG 渲染的体积、首屏时间、缓存和 WebP 替代方案需要真实样本验证。

## services/agent

- [x] `[P0]` 将 agent 平台从“组装好的对象图”补成“运行中的服务”：`create_app()` 当前只暴露 `/health`，没有 broker consumer、outbox publisher worker、startup/shutdown 生命周期管理，也没有从消息入口真正驱动 `execute_requested_job(...)`。
- [x] `[P0]` 明确唯一执行栈：旧 `app/workflows/*` LangGraph 路径已移除，仍有效的 schema 和 paper-understanding helper 已迁入 `app/agent_platform`，测试改为覆盖当前 handler/runtime 路径。
- [x] `[P0]` 修复 execution runtime 的失败状态闭环：handler、LLM、JSON parse、schema validation 任一异常都必须写回 `FAILED_RETRYABLE`、`DEAD_LETTERED` 或 `FAILED_TERMINAL`，不能让 `EXECUTION_JOB` 停留在 `RUNNING`。
- [x] `[P0]` 将 `analysis.completed` 从 runtime 返回值升级为可靠 outbox 事件：执行成功后必须写入 `EXECUTION_OUTBOX`，后续由 publisher/worker 投递并标记发布，避免进程崩溃丢 completion event。
- [x] `[P1]` 为 LLM provider 输出建立失败分类和重试语义：provider transport 失败进入 retryable，JSON/schema 错误和业务输入错误进入 terminal，并记录 `LAST_ERROR_CATEGORY`。
- [x] `[P1]` 为 LLM 输入增加预算层：按 analysis type 选择字段、限制 `pdfText`/sections 长度、记录被截断信息，避免长 PDF 直接进入 prompt 带来成本和延迟失控。
- [x] `[P1]` 统一 Agent 输出 schema 的严格性：screening、reviewer assist、conflict analysis 都应禁止额外字段，避免 strict provider 输出和本地 Pydantic 接受规则不一致。
- [ ] `[P1]` 为 provider 执行层建立真实边界：`ProviderExecutor` 目前主要是 deterministic stub，后续要把真实模型调用、超时、错误分类、幂等日志、成本控制和 provider 配置隔离到单独适配层。
- [x] `[P1]` 补齐 execution job 生命周期数据：`EXECUTION_JOB` 已新增最近错误分类、最后尝试时间、完成时间，并纳入 repository、状态机、schema verification 和 dev bootstrap。
- [x] `[P1]` 为 message-driven 路径增加 focused 集成测试：`scripts/analysis-e2e-smoke.sh` 证明 demo screening 的“requested message -> execution job -> completed event -> projection ready”可在真实 broker/DB 条件下跑通。
- [x] `[P2]` 继续清理迁移遗留：已删除旧 `app/workflows/*` workflow 入口并移除 LangGraph 运行依赖，主动文档改为只描述 `agent_platform` handler/runtime。

## database/oracle

- [x] `[P0]` 明确 legacy `AGENT_*` 表与新 `ANALYSIS_*` / `EXECUTION_*` 表的并存策略：当前 schema、seed、trigger、verify 仍同时维护两套 agent 数据模型，容易让后续开发误判真实来源；需要确定淘汰计划或显式标记 legacy only。
- [x] `[P1]` 为新的 message-driven 表继续补治理字段和查询索引：`019_execution_job_governance.sql` 增加 `LAST_ERROR_CATEGORY`、`LAST_ATTEMPT_AT`、`COMPLETED_AT` 及错误/尝试时间索引，并更新 `verify_schema.sql`。
- [ ] `[P1]` 收敛 demo seed 对 legacy agent 数据的依赖，避免真实页面已经切到新读模型，但 seed 和演示脚本仍把旧表当权威来源。
- [ ] `[P1]` 为 schema 演进补一份迁移说明，明确从 first-generation agent tables 迁移到 new intent/execution tables 的顺序、兼容边界和清理条件。
- [ ] `[P2]` 评估把页面型聚合查询沉淀为更明确的 read model 或 view，减轻 API 端大量手写 join/count 子查询的维护成本。

## scripts / docs / devops

- [x] `[P0]` 修正 `scripts/test-all.sh` 的可信度：当前脚本在 Python 依赖齐全时运行完整 `services/agent/tests/`，覆盖 execution runtime、broker worker、provider budgeting、message consumer 和 analysis flow；缺依赖时才退化为语法检查。
- [x] `[P1]` 为 `dev-up.sh`、`test-all.sh`、`README.md`、`docs/TESTING.md` 对齐当前架构阶段：开发、测试和文档入口已指向当前 Oracle/RabbitMQ/analysis platform 路径，不再把旧 HTTP task 模型描述为现行主链路。
- [x] `[P1]` 增加最小部署资产：已补齐 CI workflow、`.env.example` 和 `docs/ENVIRONMENT.md`，当前运行拓扑及环境变量有统一入口。
- [x] `[P1]` 为 RabbitMQ / Oracle / agent runtime 的联调失败增加更聚焦的诊断输出与操作指引：`scripts/analysis-e2e-smoke.sh` 会输出 API/Agent 日志路径，`docs/ENVIRONMENT.md` 和 `docs/TESTING.md` 记录联调入口。
- [ ] `[P2]` 把 docs 从“设计历史 + 当前说明混放”改成“现行实现文档 + 历史设计归档”结构，降低新开发者误读成本。

## Productization Gaps

- [ ] `[P1]` 定义最小运维面：失败重驱、死信处理、手工补偿、消息积压观察和分析任务审计查询。
- [ ] `[P1]` 明确安全基线：环境密钥注入方式、JWT secret 管理、内部 broker/consumer 信任边界、敏感日志脱敏策略。
- [ ] `[P1]` 建立生产可靠性基线：Oracle/RabbitMQ/API/Agent/Web 的备份与恢复演练、灾备/高可用拓扑、容量告警、日志保留、升级回滚和恢复时间目标不能只停留在本地 Docker 验证。
- [ ] `[P2]` 明确容量与性能基线：大 PDF 渲染、批量 round 查询、analysis projection 列表、message backlog 的容量假设与压测方式。

## Business Flow Gaps For Real Deployment

以下条目来自 2026-05-07 的业务流审查，目标是对齐真实论文审查平台（OpenReview / HotCRP / CMT 类系统）的落地需求。

- [x] `[P0]` 建立 conference-scoped 权限模型的最小闭环：chair 动作现在按 manuscript conference organizer 或 admin 授权，避免全局 `CHAIR` 横向操作其它会议；完整 track / area chair / senior PC / 委托管理仍属后续扩展。
- [x] `[P0]` 强化会议阶段和 deadline 执行语义的最小闭环：submission/bidding 既有截止校验之外，review report submit 现在按 assignment deadline 优先、round deadline 兜底硬拒；自动开关阶段、宽限/延期/reopen/late policy 仍属后续扩展。
- [x] `[P0]` 补齐 COI 与双盲治理闭环的最小阻断：直接 assignment/reassignment 现在复用 conference reviewer membership、作者本人、已记录冲突、decline bid、实时负载校验，避免绕过 draft 候选过滤；历史共同作者/导师学生/单位变体/override 审批和泄露检查仍属后续扩展。
- [x] `[P0]` 增加 author-facing decision package 的最小闭环：作者可读取正式 decision reason 和匿名化 author-visible reviews，前端作者列表已接入 decision package；response-to-reviewers、版本 diff/变更说明和多轮修回历史仍属后续扩展。
- [ ] `[P1]` 扩展投稿包：支持 supplementary files、artifact/code/data 链接、topic/track 选择、论文长度/模板检查、匿名化 checklist、伦理/IRB/AI 使用声明、作者贡献声明、preferred/excluded reviewers 和 plagiarism/format screening 结果。
- [x] `[P1]` 建立可配置表单模型的 Wave6 闭合范围：submission/review/meta-review/camera-ready 表单定义、字段类型、可见性、必填规则、active form 读取、草稿/提交响应与前端渲染均已落地；author-feedback 的深度线程表单和 track-specific phase policy 仍属后续产品深度。
- [x] `[P1]` 提升 reviewer 工作流成熟度的 Wave6 闭合范围：reviewer 动态评审表单已支持保存草稿、提交、按会议配置字段和读取 revision history；固定 legacy review report 仍保留兼容，review quality/rating 和更深 rebuttal 后更新流程留给后续扩展。
- [x] `[P1]` 增加 rebuttal / author feedback / review revision 闭环的 Wave6 闭合范围：rebuttal 提交已按 conference phase 的 open/close window 硬拒，review form 每次提交写入 revision history，author feedback/rebuttal 继续可被 chair 读取；按 review 的多线程回复和 reviewer 读后复审属于后续产品深度。
- [x] `[P1]` 增加 discussion / meta-review / area-chair 流程的最小讨论闭环：已新增 reviewer/chair discussion message 持久化和 round/assignment scoped endpoints；完整 area-chair 汇总、rebuttal response、分歧升级和 decision meeting 记录仍属后续扩展。
- [x] `[P1]` 完善 reviewer pool 运营的最小邀请/委托闭环：chair/admin 可邀请 reviewer，reviewer 可接受/拒绝，已分配 reviewer 可请求 external reviewer delegation，chair/admin 可 approve/reject；批量导入、层级 subject areas、reviewer type/group、负载变更历史、orphan paper 检测和目标评审数达成检查仍属后续扩展。
- [ ] `[P1]` 补齐 chair 批量运营能力：真实系统通常支持 CSV 批量导入/导出用户、论文、冲突、reviewer preferences、assignment、decision、paper status、tags，并在提交前展示 assignment/decision 变更预览、错误报告和确认步骤；当前只能逐条 API 操作。
- [ ] `[P1]` 增加 paper administration / shepherd 分工：真实平台常见 submission administrator、primary/secondary reviewer、optional PC reviewer、metareviewer、senior metareviewer、discussion lead、shepherd、proceedings editor 等细粒度职责；当前只有 conference organizer/admin 与普通 reviewer，缺少论文级管理权限和冲突 chair 隔离机制。
- [ ] `[P1]` 增加 chair/reviewer 工作台的搜索、标签和公式化筛选：真实平台支持按状态、track、tag、round、review 完成度、分数、冲突、负载、投票、排序公式等组合查询并驱动批量操作；当前 workbench 只有少量固定列表和过滤条件。
- [x] `[P1]` 增加自动分配与匹配治理的最小 advisory 闭环：chair/admin 可写入 conflict relationship、导入 reviewer matching score、生成不直接改写 assignment 的 proposal bundle，并记录 override audit；综合 subject-area/bid/group/quota 的完整算法、proposal confirm-to-draft、锁定 reviewer 和可视化 override 仍属后续扩展。
- [ ] `[P1]` 加入撤稿、desk-screening 和异常处理流程：需要 author withdraw、chair desk reject reason、admin/chair 退回投稿、替换损坏 PDF、撤销误分配、撤回/重开评审、撤销错误决定等受审计保护的补偿动作。
- [x] `[P1]` 建立正式通知和通信边界的最小审计闭环：decision release 现在同步写入 `COMMUNICATION_LOG` 并提供 conference-scoped communication log 查询；chair/admin 也可维护 email template、确定性 preview 并记录 fake test-send history；真实 SMTP、退信/重发、订阅、公告、批量 reminder、assignment notification、decision notification 和通信控制台仍属后续扩展。
- [x] `[P1]` 支持 offline reviewing 的最小导入闭环：reviewer 可获取 CSV header 模板，上传离线 review CSV 做 preview 且不突变 `REVIEW_REPORT`，confirm 后写入正式 review report；文件下载、覆盖预览、逐行错误 UI、审计详情和复杂表单映射仍属后续扩展。
- [x] `[P2]` 补齐 camera-ready / publication 流程的最小入口：接受稿作者可提交 camera-ready 元数据、版权确认和许可类型，chair/admin 可 accept/reject camera-ready metadata、维护 publication metadata 并生成 proceedings preview，前端作者列表已接入最小入口；真实文件存储、注册/缴费、IEEE eCopyright、proceedings editor、proceedings export/download、DOI/索引适配器、Open Academic/Search indexing metadata 和发布工作台仍属后续扩展。
- [ ] `[P2]` 支持多 track / session / artifact evaluation：真实会议常有 track chairs、special tracks、workshop/session 分组、artifact evaluation、presentation scheduling、presentation files 和跨 track 冲突策略，当前单 conference + 单 round 模型需要后续扩展。
- [x] `[P2]` 增加治理报表和数据生命周期的最小报表入口：已提供 conference-scoped governance report，覆盖 submission、accepted、assignment、submitted review、overdue、COI、camera-ready 计数；reviewer performance、review quality/rating、activity log、导出、匿名数据集、GDPR 删除和归档策略仍属后续扩展。
