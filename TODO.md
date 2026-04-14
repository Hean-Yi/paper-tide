# TODO - Known Gaps and Improvements

本清单只保留尚未完成的工作。已完成的 Task 13 P0/P1 修复项和 Task 14 Reviewer Reader / Reviewer Agent Assist 功能已从待办中移除。Task 14 复查和本地运行中发现的缺口已重新补入下方。

## P0（需要优先修复）

- [ ] 修复 Reviewer Agent Assist 本地运行认证配置：`scripts/dev-up.sh` 和文档应为 API 与 Agent service 设置同一个 `AGENT_INTERNAL_API_KEY`，避免 Python Agent 的 `/agent/tasks` 因缺少或不匹配内部 key 返回 `503/401`。
- [ ] 修复 Spring Security 错误转发被误报为 `Unauthorized`：显式放行 `/error` 或增加统一异常响应，确保 Agent service 的 `503/401` 能在前端显示真实原因，而不是被 `/error` 二次拦截成 `401`。
- [ ] 收紧 Reviewer 对旧版 Agent results 端点的访问：`GET /api/manuscripts/{manuscriptId}/versions/{versionId}/agent-results` 不应再允许 Reviewer 按版本读取全部 redacted 结果，避免看到 Chair/Admin 触发的决策冲突分析或其他上下文。
- [ ] 让 Reviewer Assist 真正按 assignment 隔离：当前复用和查询只按 `manuscriptId + versionId + roundId + taskType`，同一轮多个 reviewer 可能串读同一个 assist task；应把 `assignmentId` 纳入任务关联、查询或 payload fingerprint，并补双 reviewer 回归测试。
- [ ] 为 Reviewer Assist 响应使用专用 DTO：不要复用带 `rawResult` 字段的 `AgentResultResponse`，从响应契约上彻底移除 `rawResult`，并增加响应字符串级别断言。

## P1（推荐尽快修复）

- [ ] 在前端 Agent 面板中区分 `401/403/409/502/503` 等错误，并展示可操作文案，例如“Agent service key 未配置”“任务状态不允许重新触发”“Agent 服务暂不可用”；非 Agent 的 author/chair 操作已接入统一 API 错误提示。
- [ ] 为 Agent task 创建链路增加结构化日志和可观测性：记录本地 taskId、externalTaskId、taskType、assignmentId、Agent HTTP 状态和失败摘要，便于排查 reviewer assist 卡住或失败。
- [ ] 扩展 `scripts/test-all.sh` 的 Agent 验证范围，至少包含 `test_tasks_api.py`、`test_multipart_tasks_api.py` 和 `test_workflow_schemas.py`，不要只跑 health 子集。
- [ ] Agent 触发和冲突分析类操作补齐 loading 状态；非 Agent 的筛稿、分配、逾期标记、决策、创建轮次、桌面拒稿、PDF 上传和最终提交已完成。
- [ ] 避免 Decision Workbench 逐 round 拉取 Agent 结果导致 N+1，改为后端批量接口。
- [ ] Repository 内重复 RowMapper 提取为常量。
- [ ] 将运行环境变量集中成 `.env.example` 或 `docs/ENVIRONMENT.md`，覆盖 Oracle、JWT、API/Agent 内部 key、OpenRouter、端口和前端代理配置。

## P2（中期优化）

- [ ] 状态机由字符串判断升级为 `enum + transition table`。
- [ ] 为 Agent 任务建立更明确的数据模型：区分 version-scoped、round-scoped、assignment-scoped 任务，减少通过 payload 隐式表达作用域。
- [ ] 增加后端统一错误处理层，输出稳定 JSON 错误结构（status、code、message、traceId），让前端不依赖 `statusText`。
- [ ] 抽取前端通用 `useDialog<T>(submitFn)` composable。
- [ ] 继续扩展前端通用 `useAsyncAction` / `useApiError` 的使用范围到 Agent 面板、冲突分析和后续新页面；非 Agent author/chair 操作已完成基础接入。
- [ ] Decision Workbench 拆分为列表页 + 详情页。
- [ ] Agent 工作流增加轮次上限（Screening 2、Review Assist 4）。
- [ ] Agent polling 增加更细的重试/退避策略和失败分类，在不引入完整队列前先避免瞬时 Agent 503 直接永久失败。
- [ ] 通知发送改为事务事件异步化。

## UI/UX 改进

- [ ] 降低 Decision Workbench 信息密度，按列表/详情拆分。
- [ ] 增强异步反馈，减少全量刷新带来的闪烁。
- [ ] 强化主次按钮视觉层级。
- [ ] 提升移动端可用性（卡片布局或响应式隐藏列）。
- [ ] 提升空态/错误态引导。
- [ ] 在真实长论文样本上评估 Secure Paper Reader 的图片体积、DPI 和 PNG/WebP 策略。
