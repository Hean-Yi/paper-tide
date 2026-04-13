# TODO - Known Gaps and Improvements

本清单基于最新代码审查报告整理，按优先级执行。

## P0（应立即修复）

- [ ] Agent 缓存错误复用 `FAILED` 任务；仅应复用 `SUCCESS` 结果
- [ ] 投稿 PDF 上传缺少大小限制与文件魔数校验
- [ ] `deadline` 输入框改为 `el-date-picker`
- [ ] 破坏性操作（如 desk reject / decline）增加确认弹窗

## P1（推荐尽快修复）

- [ ] 抽取 `requireChairOrAdmin()` 到共享权限守卫
- [ ] 前端路由改为懒加载
- [ ] 非登录表单补齐校验规则
- [ ] 异步按钮补齐 loading 状态
- [ ] 下载后调用 `URL.revokeObjectURL()` 释放对象 URL

## P2（中期优化）

- [ ] 状态机由字符串判断升级为 `enum + transition table`
- [ ] 抽取前端通用 `useDialog<T>(submitFn)` composable
- [ ] Decision Workbench 拆分为列表页 + 详情页
- [ ] Agent 工作流增加轮次上限（Screening 2、Review Assist 4）
- [ ] 通知发送改为事务事件异步化

## 重复代码清理

- [ ] 合并三处 `requireChairOrAdmin()` 重复实现
- [ ] 合并 `findManuscriptForUpdate()` 的重复 `SELECT ... FOR UPDATE`
- [ ] 去除多页面重复对话框样板逻辑
- [ ] 合并/重构 Chair 页面重复的 Agent Trace 展示

## 性能与可维护性

- [ ] 避免 Decision Workbench 逐 round 拉取 Agent 结果导致 N+1
- [ ] Repository 内重复 RowMapper 提取为常量
- [ ] 安全配置 `anyRequest().permitAll()` 评估收紧为 `denyAll()`

## UI/UX 改进

- [ ] 降低 Decision Workbench 信息密度，按列表/详情拆分
- [ ] 增强异步反馈，减少全量刷新带来的闪烁
- [ ] 强化主次按钮视觉层级
- [ ] 提升移动端可用性（卡片布局或响应式隐藏列）
- [ ] 提升空态/错误态引导
