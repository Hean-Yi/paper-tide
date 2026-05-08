# 数据库表结构全解析

> **数据库**：Oracle Free 23c，用户 `review_app`，服务名 `FREEPDB1`  
> **当前表数量**：71 张业务表  
> **迁移文件**：`database/oracle/001_init.sql` 到 `025_wave3_wave6_full_closure.sql`（共 25 个版本）

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
| 遗留 Agent（已弃用） | 3 张 | 兼容旧版保留 |
| 会议表单 | 5 张 | 自定义评审表单、响应、修订 |
| 出版管理 | 5 张 | 终稿、元数据、论文集导出 |
| 通信与邮件 | 7 张 | 通知、日志、邮件模板、历史 |
| COI 与邀请 | 4 张 | 冲突关系、邀请、外部委托、匹配分 |
| 运营工具 | 8 张 | 审计日志、标签、批量操作、过滤器 |
| 文件存储 | 2 张 | 通用文件、DOI 适配器 |

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
**当前数据**：4 条（预置种子数据）

| ROLE_ID | ROLE_CODE | ROLE_NAME |
|---------|-----------|-----------|
| 1 | AUTHOR | 作者 |
| 2 | REVIEWER | 审稿人 |
| 3 | CHAIR | 程序委员会主席 |
| 4 | ADMIN | 平台管理员 |

**作用**：角色字典表，一般不会变动。角色决定了用户能访问哪些 API 端点和前端页面。

---

#### `SYS_USER_ROLE` — 用户-角色关联
**当前数据**：10 条

| 字段 | 说明 |
|------|------|
| USER_ROLE_ID | PK |
| USER_ID | FK → SYS_USER |
| ROLE_ID | FK → SYS_ROLE |

**作用**：多对多关联表。一个用户可以同时持有多个角色（如一个人既是作者又是审稿人）。Spring Security 登录时从这里查询用户角色并生成 JWT Claims。

---

#### `USER_RESEARCH_AREA` — 用户研究领域
**当前数据**：2 条

| 字段 | 说明 |
|------|------|
| USER_RESEARCH_AREA_ID | PK |
| USER_ID | FK → SYS_USER |
| AREA_CODE | 领域编码（如 `ML`、`CV`） |
| AREA_NAME | 领域名称（如"机器学习"） |

**作用**：记录用户（主要是审稿人）的研究方向，用于审稿人-论文匹配算法。与 `REVIEWER_MATCHING_SCORE` 联合使用，辅助主席分配任务。

---

#### `USER_ACADEMIC_PROFILE` — 用户学术档案
**当前数据**：0 条（注册后填写）

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
**当前数据**：0 条

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
**当前数据**：0 条

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

#### `REVIEWER_BID` — 审稿人竞标/意愿声明
**当前数据**：1 条

| 字段 | 说明 |
|------|------|
| BID_ID | PK |
| CONFERENCE_ID | FK → CONFERENCE |
| MANUSCRIPT_ID | FK → MANUSCRIPT |
| REVIEWER_ID | FK → SYS_USER |
| BID_VALUE | `WANT_TO_REVIEW`（愿意）/ `NEUTRAL`（中立）/ `DECLINE`（拒绝） |
| CONFLICT_DECLARED | 是否声明利益冲突（0/1） |
| BID_AT | 竞标时间 |

**作用**：在竞标阶段（`BIDDING_OPEN`），审稿人可以对每篇论文表明评审意愿。声明 `DECLINE` + `CONFLICT_DECLARED=1` 相当于 COI（利益冲突）声明。主席分配任务时优先考虑 `WANT_TO_REVIEW` 的审稿人。

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

**作用**：AI Agent 或主席手动生成的"候选分配方案"。每个轮次-审稿人对只有一条草稿记录（唯一约束）。主席可以查看候选列表，批准部分候选人，完成实际的 `REVIEW_ASSIGNMENT` 创建。

---

#### `ASSIGNMENT_PROPOSAL` — 分配提案
**当前数据**：0 条

| 说明 |
|------|
| AI Agent 生成的完整分配方案，包含多个候选审稿人 |

---

#### `ASSIGNMENT_PROPOSAL_BUNDLE` / `ASSIGNMENT_PROPOSAL_CONTEXT` — 提案 Bundle 与上下文
**当前数据**：均为 0 条

**作用**：存储 AI 分配建议的完整上下文数据，供主席在审阅提案时参考。

---

#### `ASSIGNMENT_OVERRIDE_AUDIT` — 分配覆盖审计
**当前数据**：0 条

**作用**：当主席覆盖 AI 推荐（例如强制分配某个 AI 不推荐的审稿人），此表记录覆盖行为和原因，作为审计追踪。

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

#### 子组 C：遗留 Agent 表（3 张，已弃用）

| 表名 | 说明 |
|------|------|
| `AGENT_ANALYSIS_TASK` | 旧版简单任务队列，已被上方双轨架构替代 |
| `AGENT_ANALYSIS_RESULT` | 旧版结果存储 |
| `AGENT_FEEDBACK` | 旧版反馈记录 |

> ⚠️ **注意**：这 3 张表已不再由应用代码写入，仅保留历史数据（参见迁移 `011_retire_legacy_agent_tables.sql`），未来版本将彻底删除。

---

### 第七组：会议自定义表单（5 张）

支持会议组织者为评审流程定制不同阶段的表单（非固定评分模板）。

---

#### `CONFERENCE_FORM_DEFINITION` — 表单定义
**当前数据**：1 条

| 字段 | 说明 |
|------|------|
| FORM_ID | PK |
| CONFERENCE_ID | FK → CONFERENCE |
| FORM_TYPE | `SUBMISSION`/`REVIEW`/`META_REVIEW`/`AUTHOR_FEEDBACK`/`CAMERA_READY` |
| FORM_NAME | 表单名称 |
| ACTIVE_FLAG | 是否启用（0/1） |

**作用**：会议可以为不同流程阶段定义专属表单，替代系统内置的固定评分结构（`REVIEW_REPORT` 是固定结构，此表支持更灵活的自定义）。

---

#### `CONFERENCE_FORM_FIELD` — 表单字段
**当前数据**：1 条

| 字段 | 说明 |
|------|------|
| FIELD_ID | PK |
| FORM_ID | FK → CONFERENCE_FORM_DEFINITION |
| FIELD_KEY | 字段标识（如 `overall_score`） |
| FIELD_LABEL | 显示标签 |
| FIELD_TYPE | `TEXT`/`LONG_TEXT`/`NUMBER`/`SCORE`/`BOOLEAN`/`SELECT` |
| VISIBILITY | `AUTHOR_VISIBLE`/`CHAIR_ONLY`/`REVIEWER_ONLY`/`PUBLIC_SUMMARY` |
| DISPLAY_ORDER | 展示顺序 |
| OPTIONS_JSON | 选项列表（SELECT 类型用） |

---

#### `REVIEW_FORM_RESPONSE` — 评审表单响应
**当前数据**：1 条

**作用**：审稿人填写自定义表单时的答案记录（JSON 格式存储）。

---

#### `REVIEW_FORM_RESPONSE_REVISION` — 评审表单修订历史
**当前数据**：0 条

**作用**：每次修改评审表单时保留修订快照，支持审计追踪。

---

#### `WORKFLOW_FORM_RESPONSE` — 工作流通用表单响应
**当前数据**：0 条

**作用**：比 `REVIEW_FORM_RESPONSE` 更通用的响应表，`SUBJECT_TYPE` 可以是 `MANUSCRIPT`（用于作者反馈、终稿等阶段的自定义表单）。

---

### 第八组：出版管理（5 张）

处理论文接收后的终稿提交到正式出版的全流程。

---

#### `CAMERA_READY_SUBMISSION` — 终稿提交（旧）
**当前数据**：0 条

> 已被 `CAMERA_READY_FILE` 替代，功能类似但字段更完整。

---

#### `CAMERA_READY_FILE` — 终稿文件
**当前数据**：0 条

| 字段 | 说明 |
|------|------|
| CAMERA_READY_FILE_ID | PK |
| MANUSCRIPT_ID / VERSION_ID | 关联信息 |
| SUBMITTED_BY | FK → SYS_USER（作者） |
| FILE_NAME / FILE_SIZE | 文件信息 |
| CHECKSUM_SHA256 | 文件 SHA-256 校验值（防篡改） |
| COPYRIGHT_CONFIRMED | 版权确认（0/1） |
| LICENSE_TYPE | 许可证类型（如 `CC-BY`） |
| FILE_STATUS | `SUBMITTED` → `ACCEPTED` / `REJECTED` |
| DECISION_NOTE | 审核意见（CLOB） |

**作用**：论文被接收后，作者上传最终出版格式的文件（终稿）。主席审核终稿格式后给出 Accepted/Rejected。

---

#### `PUBLICATION_METADATA` — 出版元数据
**当前数据**：0 条

| 字段 | 说明 |
|------|------|
| PUBLICATION_METADATA_ID | PK |
| CONFERENCE_ID / MANUSCRIPT_ID | 关联信息（每篇论文唯一） |
| DOI | 数字对象标识符（如 `10.1145/xxxx.yyyy`） |
| INDEX_KEYWORDS | 索引关键词（1000 字符） |
| PUBLICATION_STATUS | `DRAFT` → `READY_FOR_PROCEEDINGS` → `EXPORTED` → `PUBLISHED` |

**作用**：记录论文的出版元数据，为生成论文集和 DOI 注册做准备。

---

#### `PROCEEDINGS_EXPORT_BATCH` — 论文集导出批次
**当前数据**：0 条

**作用**：主席发起"导出论文集"操作时创建批次记录，包含要导出的论文列表（JSON）和导出状态。支持预览后确认导出的两步操作。

---

#### `PROCEEDINGS_EXPORT_FILE` — 导出文件
**当前数据**：0 条

**作用**：记录已导出的具体文件信息（文件名、路径、校验值等）。

---

### 第九组：通信与邮件（7 张）

---

#### `SYS_NOTIFICATION` — 系统内部通知
**当前数据**：2 条

| 字段 | 说明 |
|------|------|
| NOTIFICATION_ID | PK |
| RECEIVER_ID | FK → SYS_USER（接收人） |
| BIZ_TYPE | 业务类型（如 `MANUSCRIPT_SUBMITTED`） |
| BIZ_ID | 关联业务对象 ID |
| TITLE / CONTENT | 通知标题/内容 |
| IS_READ | 已读标记（0/1） |

**作用**：站内消息系统，用于实时通知（如"您的论文已被分配给审稿人"）。前端轮询或 WebSocket 推送此表的未读记录。

---

#### `COMMUNICATION_LOG` — 通信日志
**当前数据**：0 条

**作用**：所有系统通信（站内通知 + 邮件）的统一日志，支持按会议、稿件维度查询。

---

#### `EMAIL_TEMPLATE` — 邮件模板
**当前数据**：0 条

**作用**：按会议定义邮件模板（如"审稿邀请"、"决定通知"），支持 `ACTIVE_VERSION_ID` 指向当前使用的版本。

---

#### `EMAIL_TEMPLATE_VERSION` — 邮件模板版本
**当前数据**：0 条

**作用**：邮件模板的版本管理，`SUBJECT_TEMPLATE` 和 `BODY_TEMPLATE` 支持变量替换（如 `{{author_name}}`）。

---

#### `OUTBOUND_EMAIL_HISTORY` — 外发邮件历史
**当前数据**：0 条

**作用**：记录每一封实际发出的邮件（收件人、主题、内容、发送状态），用于审计和重发。

---

#### `COMMUNICATION_COMPOSE_BATCH` — 通信批量发送
**当前数据**：0 条

**作用**：主席向一批用户（如所有审稿人）群发通知时，记录批量发送任务。

---

#### `COMMUNICATION_REMINDER` — 通信提醒
**当前数据**：0 条

**作用**：自动提醒任务（如"截止前 3 天提醒未完成评审的审稿人"）的配置和触发记录。

---

### 第十组：COI 成熟度与邀请管理（4 张）

---

#### `CONFLICT_RELATIONSHIP` — 冲突关系（精细化 COI）
**当前数据**：0 条

| 字段 | 说明 |
|------|------|
| CONFLICT_RELATIONSHIP_ID | PK |
| CONFLICT_TYPE | 冲突类型（如 `CO_AUTHOR`、`SAME_LAB`） |
| CONFLICT_SOURCE | `MANUAL`/`PROFILE`/`IMPORT`/`BID`/`SYSTEM` |
| SEVERITY | `SOFT`（软冲突，可覆盖）/ `HARD`（硬冲突，禁止分配） |

**作用**：比 `CONFLICT_CHECK_RECORD` 更细粒度的冲突管理，区分来源和严重程度，支持主席手动标记冲突关系。

---

#### `REVIEWER_INVITATION` — 审稿人邀请
**当前数据**：0 条

| 字段 | 说明 |
|------|------|
| INVITATION_STATUS | `PENDING` → `ACCEPTED` / `DECLINED` / `EXPIRED` |
| EXPIRES_AT | 邀请过期时间 |

**作用**：主席邀请审稿人加入会议的邀请记录，支持追踪邀请状态和过期自动清理。

---

#### `EXTERNAL_REVIEWER_DELEGATION` — 外部审稿人委托
**当前数据**：0 条

**作用**：审稿人将某篇论文转委托给外部未注册用户（如学生、同事）的申请记录，需主席审批。

---

#### `REVIEWER_MATCHING_SCORE` — 审稿人匹配分数
**当前数据**：0 条

| 字段 | 说明 |
|------|------|
| MATCHING_SCORE_ID | PK |
| CONFERENCE_ID / MANUSCRIPT_ID / REVIEWER_ID | 关联信息 |
| SCORE | 匹配分数 |
| SCORE_BREAKDOWN_JSON | 分数细项（研究方向匹配度、COI 惩罚等） |

**作用**：AI 分配辅助算法为每个"论文-审稿人"对计算的匹配分数缓存表，避免每次重算。

---

### 第十一组：运营工具（8 张）

---

#### `AUDIT_LOG` — 审计日志
**当前数据**：77 条

| 字段 | 说明 |
|------|------|
| LOG_ID | PK |
| OPERATOR_ID | FK → SYS_USER（操作人） |
| OPERATION_TYPE | 操作类型（如 `MANUSCRIPT_SUBMITTED`、`DECISION_MADE`） |
| BIZ_TYPE / BIZ_ID | 操作对象类型和 ID |
| DETAIL_JSON | 操作详情（CLOB JSON） |

**作用**：记录所有重要业务操作，支持监管审计。是目前数据量最大的运营表（77 条），Oracle 触发器和 Java 服务层都会写入。

---

#### `PAPER_TAG` — 论文标签
**当前数据**：0 条

**作用**：会议主席为论文打标签（如"最佳论文候选"、"主题领域：CV"），支持会议内部的论文分类管理。

---

#### `PAPER_ROLE_ASSIGNMENT` — 论文角色分配
**当前数据**：0 条

| ROLE_TYPE | 说明 |
|-----------|------|
| PRIMARY_REVIEWER | 主审 |
| SECONDARY_REVIEWER | 副审 |
| META_REVIEWER | 元审稿人（综合仲裁） |
| DISCUSSION_LEAD | 讨论主持 |
| SHEPHERD | 督导人（帮助作者改稿） |
| PROCEEDINGS_EDITOR | 论文集编辑 |

**作用**：在标准评审之外，为论文分配更细化的角色，支持更成熟的学术会议运作模式。

---

#### `AUTHOR_FEEDBACK` — 作者反馈/申辩
**当前数据**：0 条

**作用**：在作者申辩阶段（Rebuttal），作者对审稿意见的书面回应记录，类型区分 `REBUTTAL`（申辩）、`AUTHOR_FEEDBACK`（反馈）、`REVISION_NOTE`（修改说明）。

---

#### `IMPORT_BATCH` — 导入批次
**当前数据**：0 条

**作用**：批量导入论文标签等操作的任务记录，支持"预览 → 确认应用"两步操作，防止误操作。

---

#### `BULK_OPERATION_BATCH` / `BULK_OPERATION_ROW` — 批量操作
**当前数据**：均为 0 条

**作用**：通用的批量操作框架（如批量发送通知、批量修改分配状态），`BATCH` 记录批次元信息，`ROW` 记录每行操作的结果。

---

#### `WORKBENCH_EXPORT_BATCH` — 工作台导出批次
**当前数据**：0 条

**作用**：主席工作台中"导出数据"操作（如导出评审进度 Excel）的任务记录。

---

#### `WORKBENCH_SAVED_FILTER` — 工作台保存的过滤条件
**当前数据**：0 条

**作用**：用户在工作台中保存常用的过滤/搜索条件（JSON 格式），避免每次重新设置。

---

### 第十二组：文件与 DOI（2 张）

---

#### `STORED_FILE` — 通用文件存储
**当前数据**：0 条

**作用**：系统的通用文件元数据表，存储文件名、大小、MIME 类型、存储路径/引用等。用于管理那些不直接以 BLOB 存在表中的文件。

---

#### `DOI_INDEX_ADAPTER_SUBMISSION` — DOI 索引提交记录
**当前数据**：0 条

**作用**：论文出版后向 CrossRef 等 DOI 注册机构提交的记录，跟踪提交状态（成功/失败/待处理）。

---

## 三、表与表的关系图（核心路径）

```
SYS_USER ──┬── SYS_USER_ROLE ── SYS_ROLE
           ├── USER_ACADEMIC_PROFILE
           ├── USER_RESEARCH_AREA
           └── ROLE_APPLICATION ── EMAIL_VERIFICATION_TOKEN

SYS_USER ──── MANUSCRIPT ──┬── MANUSCRIPT_VERSION ── MANUSCRIPT_AUTHOR
                 │          └── DECISION_RECORD
                 │
                 └── REVIEW_ROUND ──┬── REVIEW_ASSIGNMENT ──┬── REVIEW_REPORT
                                    │                       └── CONFLICT_CHECK_RECORD
                                    └── ASSIGNMENT_DRAFT

CONFERENCE ──┬── CONFERENCE_PHASE
             ├── CONFERENCE_REVIEWER
             └── (MANUSCRIPT.CONFERENCE_ID)

ANALYSIS_INTENT ── ANALYSIS_PROJECTION
                ── ANALYSIS_OUTBOX / INBOX
                ── EXECUTION_JOB ──┬── EXECUTION_ATTEMPT
                                   ├── EXECUTION_ARTIFACT
                                   └── EXECUTION_OUTBOX / INBOX
```

---

## 四、迁移历史与表的来源

| 迁移文件 | 新增核心表 | 目的 |
|---------|-----------|------|
| `001_init.sql` | SYS_USER, MANUSCRIPT, REVIEW_ROUND 等 13 张基础表 | 核心业务 MVP |
| `008_agent_platform_refactor.sql` | ANALYSIS_INTENT, EXECUTION_JOB 等 9 张 | AI 分析双轨架构 |
| `011_retire_legacy_agent_tables.sql` | — | 标记 AGENT_* 三表为遗留 |
| `012_registration_foundation.sql` | USER_ACADEMIC_PROFILE, ROLE_APPLICATION, EMAIL_VERIFICATION_TOKEN | 注册/审批流程 |
| `014_conference_cfp_lifecycle.sql` | CONFERENCE, CONFERENCE_PHASE | 会议管理 |
| `015_conference_scoped_submission.sql` | — | 稿件关联会议 |
| `016_reviewer_pool_bidding.sql` | CONFERENCE_REVIEWER, REVIEWER_BID | 审稿人池与竞标 |
| `017_assignment_drafts.sql` | ASSIGNMENT_DRAFT | AI 分配草稿 |
| `020_business_operations_closure.sql` | REVIEW_DISCUSSION_MESSAGE, CAMERA_READY_SUBMISSION, COMMUNICATION_LOG | 业务运营闭环 |
| `021_real_platform_wave6_wave7.sql` | CONFERENCE_FORM_DEFINITION, REVIEW_FORM_RESPONSE, AUTHOR_FEEDBACK 等 | 自定义表单 |
| `022_assignment_coi_maturity.sql` | REVIEWER_INVITATION, CONFLICT_RELATIONSHIP, REVIEWER_MATCHING_SCORE | COI 成熟度 |
| `023_publication_communication_maturity.sql` | EMAIL_TEMPLATE, CAMERA_READY_FILE, PUBLICATION_METADATA 等 | 出版与邮件 |
| `025_wave3_wave6_full_closure.sql` | WORKFLOW_FORM_RESPONSE, REVIEW_FORM_RESPONSE_REVISION | 表单修订历史 |

---

## 五、总结

**表多的根本原因是系统功能层次丰富**，一个学术会议的完整生命周期包含：

1. **用户注册审批** → 2 层表（注册申请 + 邮箱验证）
2. **会议全生命周期** → 3 层表（会议主记录 + 阶段时间 + 审稿人池）  
3. **论文多版本管理** → 3 层表（稿件 + 版本 + 作者）
4. **多轮评审** → 4 层表（轮次 + 分配 + 报告 + 决策）
5. **AI 分析双轨** → 9 张表（Outbox 模式 + 双侧状态机）
6. **出版闭环** → 5 张表（终稿 + 元数据 + 论文集 + DOI）
7. **运营可观测性** → 8 张运营工具表（审计、标签、批量操作）

其中数据量最多的运营表是 `AUDIT_LOG`（77 条），说明系统的每一次关键操作都有完整的审计轨迹。
