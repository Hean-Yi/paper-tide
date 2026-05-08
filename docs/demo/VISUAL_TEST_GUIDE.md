# 完整链路人工可视化测试指南

> 目标：覆盖从公开注册、Admin 审批、Chair 创建并发布会议、Author 投稿，到 Reviewer 申请资质、竞标、分配、评审，再到 Chair 决策和 Author 查看结果的完整流程。  
> 预计时间：60–90 分钟（含等待和切换账号时间）。

---

## 一、测试前置条件

### 1.1 服务启动

在项目根目录运行以下命令，确认三个服务均正常启动：

```bash
# 终端 1：启动后端 API（Spring Boot，端口 8080）
cd apps/api && mvn spring-boot:run

# 终端 2：启动前端（Vue 3，端口 5173）
cd apps/web && npm run dev

# 终端 3（可选）：启动 Agent 服务（端口 8001，需 AI 分析演示时才需要）
cd services/agent && python3 -m uvicorn app.main:app --reload --port 8001
```

### 1.2 数据库种子

确保 Oracle 已执行以下迁移（按顺序）：

```bash
# 用于演示的核心种子脚本（第一次运行时已有 demo 账号则跳过）
bash scripts/oracle-demo-seed.sh
```

**演示账号一览（密码均为 `demo123`）**：

| 用户名 | 角色 | 姓名 | 单位 |
|---|---|---|---|
| `admin_demo` | ADMIN | Admin Demo | Platform Ops |
| `chair_demo` | CHAIR | Chair Demo | Fudan University |
| `author_demo` | AUTHOR | Author Demo | Southeast University |
| `reviewer_demo` | REVIEWER | Reviewer Demo | Nanjing University |

> 注意：演示账号由 demo seed 预置，**已绕过注册/审批流程**。若要测试完整注册链路，请使用第二节的新账号路径。

### 1.3 浏览器准备

- 建议使用两个独立浏览器窗口（或隐私模式）分别维护不同角色的登录状态，以便快速切换。
- 前端地址：`http://localhost:5173`

---

## 二、完整链路测试步骤

### 阶段 0：公开注册与角色申请（新用户路径）

> 如只演示核心链路可跳过本阶段，使用预置账号直接从阶段 1 开始。

#### 步骤 0-1：新 Reviewer 公开注册

1. 打开 `http://localhost:5173/register`。
2. 填写注册信息：
   - 用户名：`reviewer_test`（自定义）
   - 密码：`Test123456`
   - 邮箱：`reviewer_test@example.com`
   - 真实姓名、单位
3. **注册类型选择 `REVIEWER`**（或 `Reviewer`）。
4. 填写研究方向（Area Code，如 `NLP`）和代表作。
5. 点击"提交注册"。
6. ✅ 预期：页面提示"注册成功，等待管理员审核"（邮箱验证可选，本地无真实邮件则忽略验证步骤）。

#### 步骤 0-2：新 Organizer（Chair）公开注册

1. 另开一个窗口，打开 `http://localhost:5173/register`。
2. 注册类型选择 `ORGANIZER`。
3. 填写计划会议名称（如 `ICSE 2026`），以及学术主页等信息。
4. 点击"提交注册"。
5. ✅ 预期：提示"等待管理员审核"。

#### 步骤 0-3：Admin 审批角色申请

1. 使用 `admin_demo / demo123` 登录。
2. 进入侧边栏 **Admin → Role Applications**（路由 `/admin/role-applications`）。
3. 找到刚才提交的 `reviewer_test` 和 Organizer 申请，点击 **Approve**。
4. ✅ 预期：申请状态变为 `APPROVED`，列表刷新。

---

### 阶段 1：Chair 创建会议并提交审批

> 使用 `chair_demo / demo123` 登录。

#### 步骤 1-1：创建会议草案

1. 进入 **Chair → Conference Console**（路由 `/chair/conferences`）。
2. 在"Create Conference"表单中填写：
   - 会议全名：`International Conference on Software Engineering 2026`
   - 缩写：`ICSE2026`
   - 年份：`2026`
   - Public Slug：`icse2026`
   - 评审模式：`DOUBLE_BLIND`
   - CFP 正文：一段描述会议主题的文字
   - 研究方向（用逗号分隔）：`SE,AI,Systems`
   - 每篇目标评审数：`3`
   - 每位 Reviewer 最大分配数：`4`
   - 各阶段时间（演示时可设置为宽松的未来时间，保证后续操作不会超期）：
     - Submission Open：`2026-05-01T00:00:00Z`
     - Submission Close：`2027-12-01T00:00:00Z`（宽松，保证可投稿）
     - Bidding Open：`2026-05-01T00:00:00Z`
     - Bidding Close：`2027-12-01T00:00:00Z`（宽松）
     - Review Deadline：`2027-12-31T00:00:00Z`
     - Decision Release：`2027-12-31T00:00:00Z`
3. 点击 **Create Draft**。
4. ✅ 预期：页面显示新建的 Conference 信息（包含 `conferenceId`，记录下来供后续使用，如 `conferenceId = 1`）。

#### 步骤 1-2：提交会议审批

1. 在同一页面，点击 **Submit for Approval** 按钮。
2. ✅ 预期：会议状态变为 `PENDING_APPROVAL`。

---

### 阶段 2：Admin 审批会议 CFP

> 切换到 `admin_demo / demo123`（或在另一浏览器窗口登录）。

#### 步骤 2-1：审批待审会议

1. 进入 **Chair → Conference Console**（Admin 也可访问该页面）。
2. 在 "Pending Conference Approvals" 列表中找到刚才提交的 `ICSE2026`。
3. 点击 **Approve**。
4. ✅ 预期：会议从列表移除，状态变为 `APPROVED`（或 `ACTIVE`）。

---

### 阶段 3：公开 CFP 浏览（可选展示）

> 任意未登录状态（或新窗口）。

1. 打开 `http://localhost:5173/cfp`。
2. ✅ 预期：可以看到已审批通过的 `ICSE2026` 会议卡片，包含截止时间、主题方向等信息。

---

### 阶段 4：Chair 将 Reviewer 加入会议评审池

> 切换回 `chair_demo / demo123`。

#### 步骤 4-1：加入评审池

1. 进入 **Chair → Conference Console**。
2. 在 "Add Reviewer to Conference Pool" 表单中：
   - Conference ID：填入步骤 1-1 记录的 `conferenceId`（如 `1`）
   - Reviewer ID：`1002`（即 `reviewer_demo` 的用户 ID）
   - Max Load：`4`
3. 点击 **Add Reviewer**。
4. ✅ 预期：提示 "Reviewer added to conference pool"。

> **若测试了阶段 0 的新用户**：同样将新注册的 `reviewer_test` 的用户 ID 加入评审池。

---

### 阶段 5：Author 选择会议投稿

> 切换到 `author_demo / demo123`。

#### 步骤 5-1：登录并浏览 CFP

1. 登录后进入 Dashboard，查看导航中的作者入口。
2. 浏览 `/cfp` 找到 `ICSE2026`，记录 `conferenceId`。

#### 步骤 5-2：新建稿件

1. 进入 **Author → My Manuscripts**（路由 `/author/manuscripts`）。
2. 点击"新建稿件" / **Submit**（路由 `/author/submit`）。
3. 填写稿件信息：
   - 标题：`Deep Learning for Code Review Automation`
   - 摘要：`We propose a novel deep learning model that automates code review feedback generation...`（任意摘要）
   - 关键词：`deep learning,code review,automation`
   - 选择会议：选择步骤 1 创建的 `ICSE2026`
4. ✅ 预期：稿件创建成功，记录 `manuscriptId`（如 `101`）和 `versionId`（如 `101`）。

#### 步骤 5-3：上传 PDF

1. 在稿件详情页或稿件列表中，找到上传 PDF 的操作。
2. 选择一个 PDF 文件（可用任意小 PDF，不超过默认限制即可）。
3. 点击上传。
4. ✅ 预期：PDF 上传成功，版本显示文件大小和文件名。

#### 步骤 5-4：提交版本

1. 在版本列表中，点击 **Submit**（提交该版本）。
2. ✅ 预期：稿件状态从 `DRAFT` 变为 `SUBMITTED`。

---

### 阶段 6：Reviewer 参与 Bidding（投标/声明偏好）

> 切换到 `reviewer_demo / demo123`。

#### 步骤 6-1：打开 Bidding 页面

1. 进入 **Reviewer → Bidding**（路由 `/reviewer/bidding`）。
2. 在 Conference ID 输入框中填写 `conferenceId`（如 `1`），点击加载。
3. ✅ 预期：显示该会议的可投标论文列表，包含匿名化标题。

#### 步骤 6-2：提交 Bid

1. 找到 Author 刚投稿的论文条目（双盲模式下只显示匿名信息）。
2. 选择意向（`WILLING` 愿意 / `NEUTRAL` 中立 / `CONFLICT` 利益冲突）。
3. 点击提交。
4. ✅ 预期：Bid 提交成功，列表中该论文显示已提交的意向。

---

### 阶段 7：Chair 筛选投稿（Screening Queue）

> 切换回 `chair_demo / demo123`。

#### 步骤 7-1：查看筛选队列

1. 进入 **Chair → Screening Queue**（路由 `/chair/screening`）。
2. ✅ 预期：看到 Author 提交的 `SUBMITTED` 状态稿件。

#### 步骤 7-2：启动筛选（Start Screening）

1. 点击稿件行的 **Start Screening** 按钮。
2. ✅ 预期：稿件状态变为 `UNDER_SCREENING`；页面刷新后该条目仍显示。

#### 步骤 7-3：触发 AI 筛选分析（可选，需 Agent 服务运行）

1. 点击 **Request AI Analysis** 按钮。
2. ✅ 预期：提示 "Screening analysis requested"，分析异步进行。
3. 可在 Admin → Agent Monitor 查看分析任务状态。

#### 步骤 7-4：创建评审轮次（Create Round）

1. 在该稿件行点击 **Create Round** 按钮，弹出对话框。
2. 填写 Deadline（默认为两周后，可保留）。
3. 点击确认。
4. ✅ 预期：弹窗关闭，提示 Round 创建成功，记录 `roundId`（可从返回消息或刷新后页面看到）。

---

### 阶段 8：Chair 分配 Reviewer

> 仍使用 `chair_demo / demo123`。

#### 步骤 8-1：进入分配操作工作台

1. 进入 **Chair → Assignment Operations**（路由 `/chair/assignment-operations`）。
2. 输入 Conference ID 并加载。

#### 步骤 8-2：导入 Reviewer 邀请（可选）

1. 在 "Bulk Reviewer Invitations" 区域，使用默认 CSV 样本（已预填）。
2. 点击 **Preview Invitations**，查看预览行。
3. 若预览成功，点击 **Confirm** 确认导入。
4. ✅ 预期：邀请批次显示在"Import Batches"列表中。

#### 步骤 8-3：生成 Assignment Draft

1. 进入 **Chair → Screening Queue**，找到目标稿件的评审轮次。
2. 可使用 API 生成草稿：  
   （若前端已支持）点击 **Generate Draft** 按钮，指定 `roundId`。
3. ✅ 预期：草稿生成成功，显示候选 Reviewer 列表和排序推荐。

#### 步骤 8-4：直接指派 Reviewer（无 Draft 路径）

若前端草稿流程不方便，可直接指派：
1. 在 Screening Queue 的稿件行，找到"Assign Reviewer"输入框。
2. 输入 Reviewer ID（`1002`），点击 **Assign**。
3. ✅ 预期：分配成功，提示 "Reviewer assigned"。

---

### 阶段 9：Reviewer 接受分配并提交评审

> 切换到 `reviewer_demo / demo123`。

#### 步骤 9-1：查看分配任务

1. 进入 **Reviewer → Assignments**（路由 `/reviewer/assignments`）。
2. ✅ 预期：看到刚才分配给自己的稿件评审任务，状态为 `ASSIGNED`。

#### 步骤 9-2：接受评审任务

1. 点击任务行的 **Accept** 按钮（或进入详情页操作）。
2. ✅ 预期：任务状态变为 `ACCEPTED`。
3. 如不想接受可点击 **Decline**，测试拒绝流程。

#### 步骤 9-3：在线阅读稿件（安全 PDF 阅读）

1. 点击 **View Paper** 或进入 Review Editor（路由 `/reviewer/reviews/:assignmentId`）。
2. ✅ 预期：稿件 PDF 以安全阅读模式展示（双盲合规，Reviewer 无法直接下载原始 PDF）。

#### 步骤 9-4：请求 AI 辅助分析（可选）

1. 在 Review Editor 中，找到 **Request AI Assist** 按钮（`ReviewerAgentPanel`）。
2. 点击请求。
3. ✅ 预期：提示"分析已提交"，稍后点击 **Refresh** 查看分析结果（只显示去识别化的检查清单，不显示原始分数）。

#### 步骤 9-5：填写并提交评审报告

1. 在 Review Editor 页面，填写评审意见（动态表单）：
   - 评分/推荐（按会议表单配置）
   - 摘要意见
   - 优点/缺点
   - 对作者的建议
2. 点击 **Save Draft**（保存草稿）后再点击 **Submit**。
3. ✅ 预期：任务状态变为 `SUBMITTED`，提示"评审已提交"。

---

### 阶段 10：Chair 查看评审汇总并决策

> 切换回 `chair_demo / demo123`。

#### 步骤 10-1：打开决策工作台

1. 进入 **Chair → Decision Workbench**（路由 `/chair/decisions`）。
2. ✅ 预期：看到稿件列表，含每篇稿件的评审状态汇总（已提交数 / 总分配数）。

#### 步骤 10-2：查看评审详情

1. 点击目标稿件，进入详情视图。
2. ✅ 预期：
   - 显示所有 Reviewer 的评审摘要（双盲保护，Chair 可看完整内容）。
   - 若已触发冲突分析（DECISION_CONFLICT_ANALYSIS），显示 AI 冲突摘要。

#### 步骤 10-3：触发冲突/决策 AI 分析（可选，需 Agent）

1. 在稿件决策详情页，点击 **Request Conflict Analysis**。
2. ✅ 预期：分析任务创建，刷新后可见分析结果。

#### 步骤 10-4：提交最终决策

1. 在决策工作台的稿件行，找到 Decision 操作区域。
2. 选择决策：
   - **ACCEPT**（录用）
   - **REJECT**（拒稿）
   - **REVISION_REQUIRED**（需要修改后重投）
3. 填写决策理由（`decisionReason`，必填）。
4. 点击 **Confirm**（会有确认对话框）。
5. ✅ 预期：稿件状态更新为对应结果（`ACCEPTED` / `REJECTED` / `REVISION_REQUIRED`）；决策记录写入，可追溯。

---

### 阶段 11：Author 查看决策结果

> 切换回 `author_demo / demo123`。

#### 步骤 11-1：查看稿件状态

1. 进入 **Author → My Manuscripts**（`/author/manuscripts`）。
2. ✅ 预期：目标稿件状态已更新（`ACCEPTED` / `REJECTED` / `REVISION_REQUIRED`）。

#### 步骤 11-2：查看决策包（Decision Package）

1. 点击稿件进入详情，找到 **Decision** 区域。
2. ✅ 预期：
   - 显示 Chair 填写的正式决策理由。
   - 显示匿名化的 Reviewer 评审意见（仅 author-visible 字段，不泄露 Reviewer 身份）。

#### 步骤 11-3：提交修改稿（如决策为 REVISION_REQUIRED）

1. 点击 **Submit Revision** 或 **New Version**。
2. 重新上传 PDF，填写修改说明。
3. 提交新版本。
4. ✅ 预期：新版本创建成功，稿件进入下一轮评审流程。

---

### 阶段 12：Camera-Ready 流程（录用后）

> 切换回 `author_demo / demo123`。

#### 步骤 12-1：提交 Camera-Ready 元数据

1. 在 **Author → My Manuscripts** 中找到 `ACCEPTED` 稿件。
2. 点击 **Submit Camera-Ready**。
3. 填写：最终标题、版权类型（`CC_BY` / `ALL_RIGHTS_RESERVED` 等）、许可声明。
4. 点击提交。
5. ✅ 预期：Camera-ready 状态变为 `SUBMITTED`。

#### 步骤 12-2：Chair 审核 Camera-Ready

> 切换回 `chair_demo / demo123`。

1. 进入 **Chair → Publication Operations**（路由 `/chair/publication-operations`）。
2. 找到该稿件的 camera-ready 条目。
3. 点击 **Accept** 或 **Reject**（附理由）。
4. ✅ 预期：状态更新为 `ACCEPTED` 或 `REJECTED`，Author 端可见。

---

### 阶段 13：Admin 系统监控

> 切换回 `admin_demo / demo123`。

#### 步骤 13-1：查看 AI 分析监控

1. 进入 **Admin → Agent Monitor**（路由 `/admin/agents`）。
2. 可按分析类型（`SCREENING` / `REVIEWER_ASSIST` / `CONFLICT_ANALYSIS`）和状态筛选。
3. ✅ 预期：
   - 显示所有已触发的分析意图及其执行状态（`PENDING` / `AVAILABLE` / `FAILED` 等）。
   - 支持分页和筛选。

#### 步骤 13-2：查看角色申请管理

1. 进入 **Admin → Role Applications**（路由 `/admin/role-applications`）。
2. ✅ 预期：显示所有角色申请及其状态；可补充审批操作。

---

## 三、关键状态机验证检查点

| 操作 | 预期稿件状态 |
|---|---|
| Author 创建稿件 | `DRAFT` |
| Author 提交版本 | `SUBMITTED` |
| Chair 启动筛选 | `UNDER_SCREENING` |
| Chair 创建评审轮次 | `UNDER_REVIEW` |
| Reviewer 提交所有评审报告后 | `UNDER_REVIEW`（等待决策） |
| Chair 决策 Accept | `ACCEPTED` |
| Chair 决策 Reject | `REJECTED` |
| Chair 决策 Revision Required | `REVISION_REQUIRED` |
| Author 提交修改稿 | 重新进入 `SUBMITTED`（新版本） |

---

## 四、边界与错误场景测试

### 4.1 权限隔离验证

| 场景 | 预期行为 |
|---|---|
| Reviewer 尝试访问 `/chair/screening` | 被路由守卫拦截，跳转至登录或无权限提示 |
| Author 尝试访问决策工作台 | 403 或路由守卫拦截 |
| Reviewer 尝试查看未分配给自己的稿件 | API 返回 403 / 404 |
| 未登录用户访问 `/author/manuscripts` | 重定向到 `/login` |

### 4.2 双盲保护验证

| 场景 | 预期行为 |
|---|---|
| Reviewer 在 Bidding 页面查看稿件 | 只显示匿名化标题/摘要，无作者信息 |
| Reviewer 在 Review Editor 查看论文 | 通过安全 PDF 阅读器展示，无法直接下载原始 PDF |
| Reviewer 查看 AI 辅助分析结果 | 只显示检查清单，无原始分数、推荐、完整评审文本 |
| Author 查看评审意见 | Reviewer 身份匿名化 |

### 4.3 阶段截止验证

| 场景 | 预期行为 |
|---|---|
| 投稿窗口关闭后 Author 提交 | API 返回 400，提示截止已过 |
| Bidding 窗口关闭后 Reviewer 提交 Bid | API 返回 400 |
| 评审 Deadline 过后 Reviewer 提交报告 | API 返回 400，提示 deadline exceeded |

---

## 五、AI Agent 分析链路验证（需 Agent 服务运行）

### 5.1 Screening Analysis

1. Chair 在 Screening Queue 触发 **Request AI Analysis**。
2. 进入 Admin → Agent Monitor，筛选 `SCREENING` 类型。
3. ✅ 预期：出现 `PENDING` -> `AVAILABLE` 的状态流转（或 `FAILED` 含错误原因）。

### 5.2 Reviewer Assist Analysis

1. Reviewer 在 Review Editor 中点击 **Request AI Assist**。
2. 等待后点击 Refresh。
3. ✅ 预期：显示 checklist 形式的分析摘要，无敏感字段。

### 5.3 Reviewer Assignment Assist

1. Chair 在 Screening Queue 触发 **Generate Draft with AI Assist**（若前端已支持）。
2. ✅ 预期：返回带匹配分数和推荐理由的候选 Reviewer 排序列表。

### 5.4 Conflict Analysis

1. Chair 在 Decision Workbench 触发 **Request Conflict Analysis**。
2. ✅ 预期：显示跨 Reviewer 意见的一致性/冲突摘要。

---

## 六、演示结束核查清单

完成全部流程后，请逐一确认以下项目：

- [ ] 注册 + Admin 审批流程走通
- [ ] Chair 创建 → Admin 审批 → 会议激活
- [ ] Author 投稿 → 状态流转到 SUBMITTED
- [ ] Reviewer Bidding 页面可见且可操作
- [ ] Chair 筛选 → 创建评审轮次 → 分配 Reviewer
- [ ] Reviewer 接受任务 → 在线阅读稿件
- [ ] Reviewer 提交评审报告
- [ ] Chair 查看决策工作台 → 提交最终决策
- [ ] Author 收到决策结果（状态更新 + 评审意见可见）
- [ ] AI 分析任务在 Admin Monitor 中可见且状态正确
- [ ] 权限隔离：各角色只能访问对应页面和数据

---

## 七、常见问题排查

| 问题 | 可能原因 | 排查方式 |
|---|---|---|
| 登录失败 | demo seed 未执行 / 密码错误 | 确认 `demo123` 密码，检查 Oracle 中 `SYS_USER` 是否有对应行 |
| 页面空白 / 无数据 | Token 过期 / API 未启动 | 查看浏览器 Console 网络请求，确认 API 返回 200 |
| Agent 分析一直 PENDING | Agent 服务未启动 / RabbitMQ 未连接 | 检查 `services/agent` 是否运行在 8001 端口 |
| 投稿找不到会议 | conferenceId 错误 / 会议未审批 | 确认 `/api/conferences/cfp` 返回该会议；确认会议状态为 `ACTIVE` |
| Reviewer 在 Bidding 看不到论文 | Reviewer 未加入该会议 evaluator pool | Chair 在 Conference Console 中 Add Reviewer 后重试 |
| 决策按钮不可用 | 评审轮次未完成 / 无足够评审报告 | 确认 Reviewer 已提交报告，状态变为 `SUBMITTED` |
| PDF 上传 413 错误 | 文件超出大小限制 | 换用小于默认上限（通常 20MB）的 PDF |
