# TODO - Known Gaps and Improvements

本清单只保留尚未完成的工作。已完成的 Task 13 P0/P1 修复项已从待办中移除。

## P0（下一步功能闭环）

- [ ] 实现 Reviewer 在线论文阅读器：Reviewer 只能查看渲染后的页面图片，不能下载原始 PDF。
- [ ] 移除 Reviewer 对现有 PDF attachment 下载接口的访问权限，保留 Author 自己稿件和 Chair/Admin 的下载权限。
- [ ] 新增 `GET /api/review-assignments/{assignmentId}/paper` 和 `GET /api/review-assignments/{assignmentId}/paper/pages/{pageNo}`。
- [ ] 新增 Reviewer 手动触发的 Agent Assist：`POST /api/review-assignments/{assignmentId}/agent-assist`。
- [ ] 新增 Reviewer Assist 查询接口：`GET /api/review-assignments/{assignmentId}/agent-assist`，只返回 `redactedResult`。
- [ ] 给 `AgentPollingScheduler` 增加真实定时轮询配置，保留 `pollOnce()` 供测试调用。
- [ ] 更新 `ReviewEditorView`：移除 PDF 下载按钮，加入 Secure Paper Reader 和 Reviewer Agent Panel。

## P1（推荐尽快修复）

- [ ] 非登录表单补齐 `el-form` rules 校验。
- [ ] 异步按钮补齐 loading 状态，尤其是 Agent 触发、筛稿、冲突分析和提交类操作。
- [ ] 合并 `findManuscriptForUpdate()` 的重复 `SELECT ... FOR UPDATE`。
- [ ] 避免 Decision Workbench 逐 round 拉取 Agent 结果导致 N+1，改为后端批量接口。
- [ ] Repository 内重复 RowMapper 提取为常量。

## P2（中期优化）

- [ ] 状态机由字符串判断升级为 `enum + transition table`。
- [ ] 抽取前端通用 `useDialog<T>(submitFn)` composable。
- [ ] Decision Workbench 拆分为列表页 + 详情页。
- [ ] Agent 工作流增加轮次上限（Screening 2、Review Assist 4）。
- [ ] 通知发送改为事务事件异步化。

## UI/UX 改进

- [ ] 降低 Decision Workbench 信息密度，按列表/详情拆分。
- [ ] 增强异步反馈，减少全量刷新带来的闪烁。
- [ ] 强化主次按钮视觉层级。
- [ ] 提升移动端可用性（卡片布局或响应式隐藏列）。
- [ ] 提升空态/错误态引导。
