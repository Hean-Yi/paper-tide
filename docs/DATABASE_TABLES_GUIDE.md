# 数据库表结构留档

---

## 一、为什么有这么多表？

这个系统不是简单的"提交—评审"两步流程，而是一个完整的**学术会议论文评审平台**。表的数量多，是因为业务覆盖面广，包含：

| 业务领域 | 表的数量 | 说明 |
|---------|---------|------|
| 用户与注册 | 8 张 | 账号、角色、学术档案、注册审批 |
| 稿件管理 | 3 张 | 投稿、版本、作者 |
| 会议管理 | 4 张 | 会议、阶段时间、审稿人池、竞标 |
| 评审流程 | 6 张 | 评审轮次、分配、报告、决策、讨论 |
| 分配辅助 | 5 张 | 草稿、提案、覆盖审计 |
| AI 分析（API 侧） | 4 张 | 意图、投影、收发件箱 |
| AI 执行（Agent 侧） | 5 张 | 任务、尝试、产物、收发件箱 |

---

## 二、各表详解

### 第一组：用户与权限（8 张）

---

#### `SYS_USER` — 系统用户账号
**当前数据**：10 条（4 个 demo 账号 + 6 个种子账号）

| 字段 | 类型 | 说明 |
|------|------|------|
| USER_ID | NUMBER(19) PK | 自增主键（序列 SEQ_SYS_USER） |
| USERNAME | VARCHAR2(100) UK | 登录名，全局唯一 |
| PASSWORD_HASH | VARCHAR2(255) | BCrypt 加密后的密码哈希 |
| REAL_NAME | VARCHAR2(100) | 真实姓名 |
| EMAIL | VARCHAR2(150) UK | 邮箱，全局唯一 |
| INSTITUTION | VARCHAR2(200) | 所属机构（可空） |
| STATUS | VARCHAR2(40) | `ACTIVE` / `DISABLED` / `PENDING_EMAIL_VERIFICATION` |
| CREATED_AT | TIMESTAMP | 创建时间 |

**作用**：整个系统最核心的表，所有角色（作者/审稿人/主席/管理员）都共用一张用户表，通过 `SYS_USER_ROLE` 区分角色。新注册用户初始状态为 `PENDING_EMAIL_VERIFICATION`，邮箱验证通过且管理员审批后变为 `ACTIVE`。

---

#### `SYS_ROLE` — 系统角色定义
| ROLE_ID | ROLE_CODE | ROLE_NAME |
|---------|-----------|-----------|
| 1 | AUTHOR | 作者 |
| 2 | REVIEWER | 审稿人 |
| 3 | CHAIR | 程序委员会主席 |
| 4 | ADMIN | 平台管理员 |

**作用**：角色字典表，一般不会变动。角色决定了用户能访问哪些 API 端点和前端页面。

---

#### `SYS_USER_ROLE` — 用户-角色关联
| 字段 | 说明 |
|------|------|
| USER_ROLE_ID | PK |
| USER_ID | FK → SYS_USER |
| ROLE_ID | FK → SYS_ROLE |

**作用**：多对多关联表。一个用户可以同时持有多个角色（如一个人既是作者又是审稿人）。Spring Security 登录时从这里查询用户角色并生成 JWT Claims。

---

#### `USER_RESEARCH_AREA` — 用户研究领域
| 字段 | 说明 |
|------|------|
| USER_RESEARCH_AREA_ID | PK |
| USER_ID | FK → SYS_USER |
| AREA_CODE | 领域编码（如 `ML`、`CV`） |
| AREA_NAME | 领域名称（如"机器学习"） |

**作用**：记录用户（主要是审稿人）的研究方向，用于审稿人-论文匹配算法。与 `REVIEWER_MATCHING_SCORE` 联合使用，辅助主席分配任务。

---

#### `USER_ACADEMIC_PROFILE` — 用户学术档案
| 字段 | 说明 |
|------|------|
| PROFILE_ID | PK |
| USER_ID | FK → SYS_USER（唯一，每人一条） |
| HOMEPAGE_URL | 个人主页 |
| ORCID | ORCID 标识符 |
| DBLP_URL | DBLP 主页链接 |
| GOOGLE_SCHOLAR_URL | Google Scholar 链接 |
| REPRESENTATIVE_WORKS_JSON | 代表作（JSON 数组） |
| CONFLICT_DOMAINS_JSON | 利益冲突领域（JSON 数组） |
| DEFAULT_MAX_LOAD | 默认最大审稿量（默认 3，范围 1-20） |

**作用**：审稿人的学术身份档案。`CONFLICT_DOMAINS_JSON` 用于自动 COI（利益冲突）检测；`DEFAULT_MAX_LOAD` 作为会议级别审稿量上限的基准值。

---

#### `ROLE_APPLICATION` — 注册/角色申请
| 字段 | 说明 |
|------|------|
| APPLICATION_ID | PK |
| USER_ID | FK → SYS_USER |
| REGISTRATION_TYPE | `AUTHOR` / `REVIEWER` / `ORGANIZER` |
| APPLICATION_STATUS | `PENDING_EMAIL_VERIFICATION` → `PENDING_ADMIN_APPROVAL` → `APPROVED` / `REJECTED` |
| SUBMITTED_PAYLOAD_JSON | 申请时提交的表单数据（JSON） |
| REVIEWED_BY | FK → SYS_USER（管理员） |
| REJECTION_REASON | 拒绝原因（CLOB） |

**作用**：管理用户的注册申请生命周期。新用户注册时创建一条记录，经过「邮箱验证 → 管理员审批」两步流程后，系统自动为其分配角色。每个用户每种注册类型只有一条记录（唯一约束），被拒后可重新申请（UPSERT 操作）。

---

#### `EMAIL_VERIFICATION_TOKEN` — 邮箱验证令牌
| 字段 | 说明 |
|------|------|
| TOKEN_ID | PK |
| USER_ID | FK → SYS_USER |
| ROLE_APPLICATION_ID | FK → ROLE_APPLICATION |
| TOKEN_HASH | 令牌哈希值（SHA-256，128 字节），不存明文 |
| TOKEN_PURPOSE | 固定为 `EMAIL_VERIFICATION` |
| EXPIRES_AT | 过期时间 |
| CONSUMED_AT | 使用时间（NULL 表示未使用） |

**作用**：安全存储邮箱验证链接中的令牌。用户收到验证邮件后，点击链接携带令牌参数，系统对比哈希值确认真实性，并在此表记录消费时间（防止二次使用）。

---

### 第二组：稿件管理（3 张）

这 3 张表构成论文投稿的核心数据模型，采用"稿件-版本"两级结构来支持多次修改/重投。

---

#### `MANUSCRIPT` — 稿件主记录
**当前数据**：1 条

| 字段 | 说明 |
|------|------|
| MANUSCRIPT_ID | PK |
| SUBMITTER_ID | FK → SYS_USER（投稿人） |
| CONFERENCE_ID | FK → CONFERENCE（所属会议） |
| CURRENT_VERSION_ID | FK → MANUSCRIPT_VERSION（当前版本，可延迟约束） |
| CURRENT_STATUS | 当前状态（见下方状态机） |
| CURRENT_ROUND_NO | 当前评审轮次编号 |
| BLIND_MODE | `DOUBLE_BLIND` / `SINGLE_BLIND` / `OPEN` |
| SUBMITTED_AT | 首次提交时间 |
| LAST_DECISION_CODE | 最新决定代码 |

**状态机**：
```
DRAFT → SUBMITTED → UNDER_SCREENING → DESK_REJECTED（桌面拒绝）
                                     ↓
                              UNDER_REVIEW → REVISION_REQUIRED
                                           → ACCEPTED
                                           → REJECTED
                              REVISED_SUBMITTED → UNDER_REVIEW（新轮次）
```

---

#### `MANUSCRIPT_VERSION` — 稿件版本
**当前数据**：1 条

| 字段 | 说明 |
|------|------|
| VERSION_ID | PK |
| MANUSCRIPT_ID | FK → MANUSCRIPT |
| VERSION_NO | 版本号（从 1 开始） |
| VERSION_TYPE | `INITIAL`（首投）/ `REVISION`（修改版）/ `RESUBMISSION`（重投） |
| TITLE | 论文标题（500 字符） |
| ABSTRACT | 摘要（CLOB） |
| KEYWORDS | 关键词（500 字符） |
| PDF_FILE | 论文 PDF 文件（BLOB，直接存数据库） |
| PDF_FILE_NAME | 文件名 |
| PDF_FILE_SIZE | 文件大小（字节） |
| SUBMITTED_BY | FK → SYS_USER |
| SUBMITTED_AT | 提交时间 |
| SOURCE_DECISION_ID | FK → DECISION_RECORD（触发本版本的决定，修订时有值） |

**作用**：每次投稿或修改都产生一条新版本记录。PDF 文件直接以 BLOB 存储在 Oracle 中，避免了文件服务器的额外部署。

---

#### `MANUSCRIPT_AUTHOR` — 稿件作者
**当前数据**：1 条

| 字段 | 说明 |
|------|------|
| MANUSCRIPT_AUTHOR_ID | PK |
| MANUSCRIPT_ID / VERSION_ID | 关联稿件和版本 |
| USER_ID | FK → SYS_USER（系统内用户，可空） |
| AUTHOR_NAME | 作者姓名（必填，支持外部作者） |
| EMAIL / INSTITUTION | 邮箱和机构 |
| AUTHOR_ORDER | 作者排序（从 1 开始） |
| IS_CORRESPONDING | 是否通讯作者（0/1） |
| IS_EXTERNAL | 是否系统外部作者（0/1） |

**作用**：一篇论文可能有多个作者，且并非所有作者都是系统注册用户（外部合作者）。双盲评审时，这里的作者信息对审稿人不可见。

---

### 第三组：会议管理（4 张）

---

#### `CONFERENCE` — 会议
**当前数据**：3 条（含 1 条遗留默认会议 + 2 条 demo 会议）

| 字段 | 说明 |
|------|------|
| CONFERENCE_ID | PK |
| NAME | 会议全称（300 字符） |
| ACRONYM | 会议缩写（60 字符，如 "CVPR"） |
| CONFERENCE_YEAR | 会议年份 |
| ORGANIZER_USER_ID | FK → SYS_USER（组织者） |
| CONFERENCE_STATUS | 当前阶段（见下方） |
| BLIND_MODE | 审稿盲审模式 |
| CFP_TEXT | 征稿启事正文（CLOB） |
| TOPIC_AREAS_JSON | 主题领域（JSON 数组） |
| TARGET_REVIEWS_PER_PAPER | 每篇论文目标审稿数（默认 3，范围 1-10） |
| DEFAULT_REVIEWER_MAX_LOAD | 审稿人默认最大审稿量（默认 3） |
| PUBLIC_SLUG | 公开 URL 友好标识（唯一） |
| CFP_PUBLISHED | 是否公开发布 CFP（0/1） |

**状态机**：
```
DRAFT → PENDING_APPROVAL → OPEN_FOR_SUBMISSION → SUBMISSION_CLOSED
                                                  → BIDDING_OPEN → REVIEW_ASSIGNMENT
                                                                  → REVIEWING → DECISION → CLOSED
```

---

#### `CONFERENCE_PHASE` — 会议阶段时间节点
**当前数据**：3 条

| 字段 | 说明 |
|------|------|
| PHASE_ID | PK |
| CONFERENCE_ID | FK → CONFERENCE（每个会议唯一一条） |
| SUBMISSION_OPEN_AT | 投稿开始时间 |
| SUBMISSION_CLOSE_AT | 投稿截止时间 |
| BIDDING_OPEN_AT | 竞标开始时间 |
| BIDDING_CLOSE_AT | 竞标截止时间 |
| REVIEW_DEADLINE_AT | 评审截止时间 |
| DECISION_RELEASE_AT | 决定发布时间 |
| REBUTTAL_OPEN_AT / CLOSE_AT | 作者申辩时间窗口（可空） |
| CAMERA_READY_OPEN_AT / CLOSE_AT | 终稿提交时间窗口（可空） |

**作用**：控制会议各阶段的时间边界，系统根据当前时间与这些时间戳对照，决定是否允许某些操作（如提交、竞标）。约束保证时间顺序正确（CHECK 约束）。

---

#### `CONFERENCE_REVIEWER` — 会议审稿人池
**当前数据**：3 条

| 字段 | 说明 |
|------|------|
| CONFERENCE_REVIEWER_ID | PK |
| CONFERENCE_ID | FK → CONFERENCE |
| REVIEWER_ID | FK → SYS_USER |
| MAX_LOAD | 本会议审稿量上限（1-20） |
| RESEARCH_AREAS_JSON | 研究方向（JSON 数组） |
| MEMBERSHIP_STATUS | `ACTIVE` / `REMOVED` |
| INVITED_BY | FK → SYS_USER（邀请人） |

**作用**：记录哪些审稿人加入了哪个会议的审稿委员会。每个会议可以有独立的审稿量上限设置，与用户全局的 `DEFAULT_MAX_LOAD` 分离。删除会议审稿人时使用软删除（`REMOVED`），保留历史记录。

---

### 第四组：评审流程（6 张）

这是系统最核心的业务模型，记录完整的同行评审过程。

---

#### `REVIEW_ROUND` — 评审轮次
**当前数据**：1 条

| 字段 | 说明 |
|------|------|
| ROUND_ID | PK |
| MANUSCRIPT_ID | FK → MANUSCRIPT |
| ROUND_NO | 轮次编号（每篇稿件从 1 开始） |
| VERSION_ID | FK → MANUSCRIPT_VERSION（本轮评审的版本） |
| ROUND_STATUS | `PENDING` → `IN_PROGRESS` → `COMPLETED` / `CANCELLED` |
| ASSIGNMENT_STRATEGY | `REUSE_REVIEWERS`（沿用上轮审稿人）/ `REALLOCATE_REVIEWERS`（重新分配） |
| SCREENING_REQUIRED | 是否需要初筛（0/1） |
| DEADLINE_AT | 本轮评审截止时间 |
| CREATED_BY | FK → SYS_USER（主席） |

**作用**：一篇论文经历修改后需要新一轮评审，每轮独立记录状态。`ASSIGNMENT_STRATEGY` 决定修改稿是否发给同一批审稿人（重大修改通常沿用，小修通常仅主席决定）。

---

#### `REVIEW_ASSIGNMENT` — 评审分配任务
**当前数据**：1 条

| 字段 | 说明 |
|------|------|
| ASSIGNMENT_ID | PK |
| ROUND_ID / MANUSCRIPT_ID / VERSION_ID | 关联信息 |
| REVIEWER_ID | FK → SYS_USER（审稿人） |
| TASK_STATUS | 任务状态（见下方） |
| ASSIGNED_AT | 分配时间 |
| ACCEPTED_AT / DECLINED_AT | 接受/拒绝时间 |
| DECLINE_REASON | 拒绝原因（CLOB） |
| DEADLINE_AT | 个人评审截止时间 |
| SUBMITTED_AT | 提交评审报告时间 |
| REASSIGNED_FROM_ID | FK → REVIEW_ASSIGNMENT（若为重新分配，记录原任务） |

**任务状态机**：
```
ASSIGNED → ACCEPTED → IN_REVIEW → SUBMITTED
         → DECLINED
         → OVERDUE（逾期）
         → REASSIGNED（被替换）
         → CANCELLED
```

---

#### `CONFLICT_CHECK_RECORD` — 利益冲突检查记录
**当前数据**：1 条

| 字段 | 说明 |
|------|------|
| CONFLICT_ID | PK |
| ASSIGNMENT_ID | FK → REVIEW_ASSIGNMENT（可空，系统检测时可能还未分配） |
| MANUSCRIPT_ID / REVIEWER_ID | 关联信息 |
| CONFLICT_TYPE | 冲突类型（如 `CO_AUTHOR`、`SAME_INSTITUTION`） |
| CONFLICT_DESC | 冲突描述（CLOB） |
| SOURCE | `SYSTEM_DETECTED`（系统自动发现）/ `SELF_DECLARED`（审稿人自行申报） |
| DECLARED_BY | FK → SYS_USER（自申报人） |
| CONFIRMED_BY_CHAIR | FK → SYS_USER（主席确认人） |

**作用**：记录审稿人与论文之间的利益冲突关系。系统在分配任务时自动检测（比对作者机构、研究领域、合著关系），也允许审稿人主动申报。

---

#### `REVIEW_REPORT` — 评审报告（结构化）
**当前数据**：1 条

| 字段 | 说明 |
|------|------|
| REVIEW_ID | PK |
| ASSIGNMENT_ID | FK → REVIEW_ASSIGNMENT（唯一，每个分配最多一份报告） |
| NOVELTY_SCORE | 新颖性评分（1-5） |
| METHOD_SCORE | 方法论评分（1-5） |
| EXPERIMENT_SCORE | 实验评分（1-5） |
| WRITING_SCORE | 写作质量评分（1-5） |
| OVERALL_SCORE | 综合评分（1-5） |
| CONFIDENCE_LEVEL | 评审人置信度（`HIGH` / `MEDIUM` / `LOW`） |
| STRENGTHS / WEAKNESSES | 优点和不足（CLOB） |
| COMMENTS_TO_AUTHOR | 给作者的评论（CLOB，在适当时机公开给作者） |
| COMMENTS_TO_CHAIR | 仅给主席的私密评论（CLOB，永不公开给作者） |
| RECOMMENDATION | `ACCEPT` / `REJECT` / `MINOR_REVISION` / `MAJOR_REVISION` / `DESK_REJECT` |

**作用**：审稿人完成评审后提交的结构化意见。`COMMENTS_TO_CHAIR` 中可以包含对作者身份的猜测或其他不宜公开的内容，系统保证双盲隔离。

---

#### `DECISION_RECORD` — 决策记录
**当前数据**：1 条

| 字段 | 说明 |
|------|------|
| DECISION_ID | PK |
| MANUSCRIPT_ID / ROUND_ID / VERSION_ID | 关联信息 |
| DECISION_CODE | `ACCEPT` / `REJECT` / `MINOR_REVISION` / `MAJOR_REVISION` / `DESK_REJECT` |
| DECISION_REASON | 决策理由（CLOB，发送给作者） |
| DECIDED_BY | FK → SYS_USER（主席） |
| DECIDED_AT | 决策时间 |

**作用**：记录主席对一篇论文某一轮评审的最终决定。每轮次只有一条决定记录（唯一约束）。`DECISION_CODE` 决定了稿件状态的后续走向：`MINOR_REVISION`/`MAJOR_REVISION` 会触发作者修改，`ACCEPT` 触发终稿流程，`REJECT` 终止流程。

---

#### `REVIEW_DISCUSSION_MESSAGE` — 评审讨论消息
**当前数据**：0 条

| 字段 | 说明 |
|------|------|
| MESSAGE_ID | PK |
| ROUND_ID / MANUSCRIPT_ID | 关联信息 |
| ASSIGNMENT_ID | 可空（轮次级讨论时为空） |
| SENDER_ID | FK → SYS_USER |
| SENDER_ROLE | `CHAIR` / `REVIEWER` |
| MESSAGE_SCOPE | `ROUND`（轮次讨论）/ `ASSIGNMENT`（一对一私信）/ `META_REVIEW`（元评审） |
| MESSAGE_TEXT | 消息内容（CLOB） |

**作用**：主席和审稿人之间的结构化讨论区，支持三种范围：轮次级全体讨论、针对特定审稿人的私信、主席的元评审（综合各审稿意见的总结评审）。

---

### 第五组：分配辅助（5 张）

这组表支持 AI 辅助分配和主席手动调整的工作流。

---

#### `ASSIGNMENT_DRAFT` — 分配草稿
**当前数据**：1 条

| 字段 | 说明 |
|------|------|
| ASSIGNMENT_DRAFT_ID | PK |
| ROUND_ID / MANUSCRIPT_ID / VERSION_ID / REVIEWER_ID | 关联信息 |
| RANK_ORDER | 推荐排名（越小越优先） |
| SCORE | 匹配分数 |
| CURRENT_LOAD / MAX_LOAD | 当前负载/最大负载 |
| BID_VALUE | 竞标意愿（`WANT_TO_REVIEW` 等，可空） |
| REASON | 推荐理由（500 字符） |
| DRAFT_STATUS | `PROPOSED`（草稿）→ `CONFIRMED`（已确认）/ `DISMISSED`（已驳回） |

---

### 第六组：AI 分析平台（9 张）

系统采用**双轨架构**：API 侧（Java/Oracle）和 Agent 侧（Python/FastAPI），通过 RabbitMQ 消息队列通信，每侧各有独立的收发件箱（Outbox 模式）和状态机。

---

#### 子组 A：API 侧（4 张）

**`ANALYSIS_INTENT` — 分析意图**
**当前数据**：4 条

| 字段 | 说明 |
|------|------|
| INTENT_ID | PK |
| ANALYSIS_TYPE | `SCREENING`（初筛）/ `REVIEWER_ASSIST`（审稿辅助）/ `CONFLICT_ANALYSIS`（冲突分析） |
| BUSINESS_ANCHOR_TYPE | 业务锚点类型（`ASSIGNMENT`/`ROUND`/`MANUSCRIPT`/`MANUSCRIPT_VERSION`） |
| BUSINESS_ANCHOR_ID / VERSION_ID | 业务对象 ID |
| REQUESTED_BY | FK → SYS_USER（请求人） |
| IDEMPOTENCY_KEY | 幂等键（防重复触发），VARCHAR2(200) 唯一 |
| BUSINESS_STATUS | `REQUESTED` → `AVAILABLE` / `FAILED_VISIBLE` / `SUPERSEDED` |
| EXECUTION_JOB_ID | FK → EXECUTION_JOB（延迟约束） |

**作用**：每次用户触发 AI 分析都创建一条意图记录。幂等键确保同一分析不会重复执行；`SUPERSEDED` 状态表示有更新的分析替代了本条（如强制重跑后旧结果失效）。

---

**`ANALYSIS_PROJECTION` — 分析结果投影**
**当前数据**：3 条

| 字段 | 说明 |
|------|------|
| PROJECTION_ID | PK |
| INTENT_ID | FK → ANALYSIS_INTENT（唯一） |
| VISIBILITY_LEVEL | `NONE`（不可见）/ `REDACTED_ONLY`（脱敏版）/ `RAW_AND_REDACTED`（双版本） |
| BUSINESS_STATUS | 与意图状态同步 |
| SUMMARY_TEXT | 面向用户的摘要文本（CLOB） |
| REDACTED_RESULT | 脱敏后的 JSON 结果（给审稿人看，CLOB） |
| RAW_RESULT_REFERENCE | 原始结果引用（给主席用） |
| IS_SUPERSEDED | 是否已被新版本替代（0/1） |

**作用**：AI 结果的读模型（Read Model）。区分脱敏版和原始版，实现双盲评审的数据隔离：审稿人只能看到 `REDACTED_RESULT`，主席可以看到原始结果。

---

**`ANALYSIS_OUTBOX` — 分析发件箱**
**当前数据**：4 条

**作用**：实现 Outbox 模式——API 侧向 Agent 发送分析请求时，先写入此表（与业务数据同一事务），再由调度器异步发布到 RabbitMQ，确保"至少一次"消息投递语义。

---

**`ANALYSIS_INBOX` — 分析收件箱**
**当前数据**：3 条

**作用**：API 侧接收来自 Agent 的分析完成事件时，先写入此表，再异步处理（更新 Intent/Projection 状态）。防止重复处理（幂等键）。

---

#### 子组 B：Agent 侧（5 张）

**`EXECUTION_JOB` — 执行任务**
**当前数据**：4 条

| 字段 | 说明 |
|------|------|
| JOB_ID | VARCHAR2(100) PK（UUID 字符串） |
| INTENT_ID | FK → ANALYSIS_INTENT |
| IDEMPOTENCY_KEY | 幂等键（200 字符唯一） |
| ANALYSIS_TYPE | 分析类型 |
| EXECUTION_STATE | `QUEUED` → `DISPATCHED` → `RUNNING` → `SUCCEEDED` / `FAILED_RETRYABLE` / `FAILED_TERMINAL` / `DEAD_LETTERED` |
| INPUT_SNAPSHOT | LLM 输入快照（CLOB，用于重试和调试） |
| FAILURE_REASON | 失败原因（CLOB） |

**作用**：Agent 侧的任务主记录，与 API 侧的 `ANALYSIS_INTENT` 通过 `JOB_ID` 双向关联（延迟约束解决先有鸡还是先有蛋的问题）。

---

**`EXECUTION_ATTEMPT` — 执行尝试**
**当前数据**：0 条

**作用**：记录每次 LLM 调用尝试的详情（开始/结束时间、使用的模型提供商、错误信息）。支持可重试失败（`FAILED_RETRYABLE`）下的多次尝试追踪。

---

**`EXECUTION_ARTIFACT` — 执行产物**
**当前数据**：0 条

| 字段 | 说明 |
|------|------|
| ARTIFACT_TYPE | `RAW_RESULT`（原始结果）/ `REDACTED_RESULT`（脱敏结果）/ `SUMMARY_PROJECTION`（摘要）/ `ERROR_REPORT`（错误报告） |
| ARTIFACT_DATA | JSON 数据（CLOB） |

**作用**：存储 LLM 执行产生的各类输出文件，按类型区分。

---

**`EXECUTION_OUTBOX` / `EXECUTION_INBOX`**

**作用**：Agent 侧对应的 Outbox/Inbox，用于向 API 侧发送完成事件，与 `ANALYSIS_OUTBOX`/`ANALYSIS_INBOX` 配合形成完整的双向消息通道。

---

