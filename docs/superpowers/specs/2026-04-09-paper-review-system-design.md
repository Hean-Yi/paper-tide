# 智能论文评审系统软件设计说明书

## 1. 文档概述

### 1.1 文档目的

本文档用于说明“智能论文评审系统”的总体设计方案，覆盖系统架构、角色与用例、功能模块、数据库设计、状态机、关键时序、Agent 架构、接口设计、部署方案与课程答辩展示重点。文档目标是形成一份可直接用于课程设计报告、软件设计说明书撰写和后续实现落地的正式设计文档。

### 1.2 项目背景

论文评审系统相较于图书管理、酒店管理等传统管理系统，更适合引入 Agent 技术。其核心业务涉及长文本理解、多维度评估、评审意见生成与冲突总结，天然适配 LLM + Agent 的“推理、工具调用、结构化输出”能力。同时，论文评审仍属于高风险决策场景，因此本系统坚持“Agent 辅助而非替代人工”的原则：AI 只提供分析参考，正式评审意见和最终决策仍由评审员与主席完成。

### 1.3 设计目标

- 构建一个完整的论文投稿、评审、决策、通知和多轮重审闭环系统
- 使用 Oracle 作为核心数据库，满足课程对数据库设计的要求
- 将 Agent 能力设计为独立智能服务，避免与主业务强耦合
- 保证双盲评审场景下的信息隔离与脱敏
- 使系统具备清晰的状态机、可解释的数据模型和可落地的技术架构

### 1.4 技术选型总览

- 前端：`Vue 3 + Element Plus`
- 主系统：`Java + Spring Boot + Spring Security + JWT`
- Agent Service：`Python + FastAPI + LangGraph`
- 数据库：`Oracle`
- 文件存储：Oracle `BLOB`
- 长文本与 JSON：Oracle `CLOB`

## 2. 系统总体设计

### 2.1 总体架构

系统采用“前后端分离 + 主系统与 Agent Service 分离”的总体结构：

1. `前端表示层`
提供作者、评审员、主席、管理员的统一 Web 操作界面。

2. `主系统业务层`
负责用户权限、投稿管理、评审分配、评审意见、主席决策、通知日志、事务控制与 Oracle 访问。

3. `Agent Service`
负责论文解析、评审辅助分析、评审冲突总结、结果脱敏前处理与结构化输出。

4. `Oracle 数据库`
作为唯一权威数据源，保存业务数据、论文文件、分析结果、通知与审计日志。

### 2.2 架构职责边界

#### 主系统负责

- 用户认证与角色权限
- 稿件状态机与评审任务状态机
- 论文版本管理与轮次管理
- 主席决策与事务一致性
- Agent 任务创建、轮询、结果入库
- 角色可见范围控制与双盲展示

#### Agent Service 负责

- PDF 解析与文本结构提取
- 基于任务类型的分析工作流执行
- 多 Agent 协作与结果汇总
- 结果 schema 校验与脱敏结果生成
- 返回结构化分析结果

#### Oracle 负责

- 核心业务数据持久化
- 文件 BLOB 存储
- 长文本与 JSON 结果存储
- 序列、约束、索引、触发器和存储过程支撑

### 2.3 技术栈选择理由

#### 2.3.1 前端

选择 `Vue 3 + Element Plus`，原因是其适合管理类系统页面，便于快速实现表单、表格、状态筛选、详情页和管理后台布局。

#### 2.3.2 主系统

选择 `Java + Spring Boot`，原因是：

- 与 Oracle 集成自然
- 适合实现分层架构、事务和权限体系
- 更符合课程设计中“管理信息系统”的常规技术路线
- 易于解释状态机、Service 层事务和接口设计

#### 2.3.3 Agent Service

选择 `Python + FastAPI + LangGraph`，原因是：

- Python 在 LLM 与 Agent 生态上更成熟
- FastAPI 便于快速提供内部服务接口
- LangGraph 适合承载带状态的 Agent 编排工作流

### 2.4 主系统与 Agent Service 的通信方式

主系统与 Agent Service 采用`异步任务模型`：

1. 主系统创建 Agent 分析任务记录
2. 主系统从 Oracle 读取 PDF BLOB
3. 主系统以 `multipart/form-data` 向 Agent Service 发送任务元数据和 PDF 文件
4. Agent Service 接收后返回 `taskId`
5. 主系统记录任务状态并以退避策略轮询结果
6. Agent Service 完成分析并提供结果查询接口
7. 主系统拉取结果、进行二次校验、写入 Oracle

### 2.5 轮询与降级策略

- 轮询采用分阶段退避策略，避免高频固定轮询
- Agent 分析失败或超时不阻塞人工评审主流程
- 主系统轮询超过设定时限后可主动将任务标记为 `FAILED`
- 主席或管理员可选择重试任务

### 2.6 双盲评审定位

系统采用`双盲评审`模式：

- 作者不可查看评审员身份
- 评审员不可查看作者姓名、单位、联系方式等信息
- 主席和管理员可在授权范围内查看完整信息
- Agent 输出给评审员的结果必须经过脱敏处理

## 3. 角色与用例设计

### 3.1 作者（Author）

#### 主要职责

- 注册与登录
- 提交论文元数据与 PDF
- 查看投稿状态
- 接收主席决定
- 提交修改稿
- 查看历史版本与历史决定

#### 权限边界

- 只能访问自己提交的稿件
- 不可查看评审员身份
- 不可查看内部评审过程及内部 Agent 分析结果

### 3.2 评审员（Reviewer）

#### 主要职责

- 查看被分配任务
- 接受任务
- 拒绝任务
- 主动声明利益冲突
- 查看匿名论文与 Agent 辅助分析
- 填写结构化评审表
- 对修改稿再次评审
- 查看历史评审记录

#### 权限边界

- 只能访问被分配稿件
- 双盲模式下仅查看匿名信息
- 正式评审意见必须由本人提交，Agent 结果仅供参考

#### 拒绝/冲突声明流程

`主席分配 -> 评审员接受 / 拒绝 / 声明冲突 -> 系统通知主席 -> 主席重新分配`

### 3.3 主席（Chair）

#### 主要职责

- 查看全部投稿
- 开始初筛
- 完成初筛决定
- 设置评审轮次和截止时间
- 分配评审员
- 重新分配评审员
- 查看潜在利益冲突提示
- 监控评审进度
- 查看 Agent 结果与评审意见
- 作出录用、拒稿、修改后重审等决定

#### 权限边界

- 拥有全局稿件和轮次管理权限
- 拥有最终业务裁决权
- 可查看双盲背后的完整身份信息

#### 初筛定义

初筛主要用于形式与范围审查，包括：

- 主题是否属于目标领域
- 投稿材料是否完整
- 基础格式是否满足送审条件

初筛不通过即 `DESK_REJECTED`，不进入正式评审流程。

### 3.4 管理员（Administrator）

#### 主要职责

- 用户账户与角色管理
- 系统运行参数管理
- 日志与异常查看
- 通知模板管理
- Agent 服务状态监控

#### 权限边界

- 只负责平台级运维与安全
- 不参与学术裁决
- 不负责会议规则、评审轮次和截止时间设置

### 3.5 截止时间与超时处理

- 主席设置每轮评审截止时间
- 系统向评审员发送临近截止提醒
- 超时任务进入 `OVERDUE`
- 主席可选择催办、允许补交或重新分配

### 3.6 通知触发关系

- 作者提交论文 -> 通知主席
- 主席分配评审任务 -> 通知评审员
- 评审员接受任务 -> 通知主席
- 评审员拒绝任务 -> 通知主席
- 评审员声明冲突 -> 通知主席
- 主席重新分配 -> 通知新评审员
- Agent 分析完成 -> 通知主席与相关评审员
- 评审员提交评审 -> 通知主席
- 任务临近截止 -> 通知评审员
- 任务超时 -> 通知主席
- 主席作出决定 -> 通知作者
- 作者提交修改稿 -> 通知主席

## 4. 功能模块设计

系统划分为五个核心模块：

1. 投稿与稿件版本管理模块
2. 评审任务与分配管理模块
3. 评审与决策管理模块
4. Agent 接入与分析结果管理模块
5. 通知与日志管理模块

### 4.1 投稿与稿件版本管理模块

#### 主要功能

- 创建稿件元数据
- 维护稿件草稿与正式提交
- 创建稿件版本
- 上传 PDF
- 记录历史版本与来源决策
- 支持修改稿提交

#### 核心设计点

- `DESK_REJECTED` 的稿件不进入评审流程
- 每次修改稿生成新的版本号
- 稿件主对象与版本对象分离建模

### 4.2 评审任务与分配管理模块

#### 主要功能

- 创建评审轮次
- 分配评审员
- 系统自动检测潜在利益冲突
- 评审员接受、拒绝或声明冲突
- 记录拒绝统计
- 截止时间管理
- 超时催办与重新分配

#### 核心设计点

- 至少支持同机构冲突检测
- 记录评审员拒绝原因与冲突来源
- 保留拒绝次数统计，供主席查看

### 4.3 评审与决策管理模块

#### 主要功能

- 提交结构化评审表
- 支持多评分维度与文字评语
- 记录评审员自评置信度
- 汇总多位评审意见
- 主席作出正式决定

#### 决策结果

- `ACCEPT`
- `REJECT`
- `DESK_REJECT`
- `MINOR_REVISION`
- `MAJOR_REVISION`

### 4.4 Agent 接入与分析结果管理模块

#### 主要功能

- 创建 Agent 任务
- 轮询任务状态
- 拉取结果并进行二次校验
- 存储原始结果与脱敏结果
- 按角色展示不同视图
- 支持修改稿重新分析

#### 核心设计点

- Agent Service 不直接访问 Oracle
- 同一稿件版本分析结果优先复用缓存
- 评审员只读取脱敏结果

### 4.5 通知与日志管理模块

#### 主要功能

- 站内消息通知
- 截止时间提醒
- 异常与失败补偿
- 审计日志记录

#### 设计原则

- 通知逻辑放在业务层
- Oracle 触发器仅用于审计辅助，不承担核心业务通知

## 5. 状态机设计

### 5.1 稿件状态机

稿件主状态包括：

- `DRAFT`
- `SUBMITTED`
- `UNDER_SCREENING`
- `DESK_REJECTED`
- `UNDER_REVIEW`
- `REVISION_REQUIRED`
- `REVISED_SUBMITTED`
- `ACCEPTED`
- `REJECTED`

#### 关键转换

- `DRAFT -> SUBMITTED`：作者提交初稿
- `SUBMITTED -> UNDER_SCREENING`：主席点击开始筛查
- `UNDER_SCREENING -> DESK_REJECTED`：主席初筛不通过
- `UNDER_SCREENING -> UNDER_REVIEW`：主席初筛通过
- `UNDER_REVIEW -> ACCEPTED / REJECTED / REVISION_REQUIRED`：主席完成决策
- `REVISION_REQUIRED -> REVISED_SUBMITTED`：作者提交修改稿
- `REVISED_SUBMITTED -> UNDER_SCREENING`：修改稿需要重新筛查
- `REVISED_SUBMITTED -> UNDER_REVIEW`：修改稿无需重筛，直接进入下一轮评审

### 5.2 修改后重审策略

修改后重审采用可配置策略，由主席在轮次配置中选择：

- `REUSE_REVIEWERS`：沿用原评审员
- `REALLOCATE_REVIEWERS`：重新分配评审员

### 5.3 评审任务状态机

评审任务状态包括：

- `ASSIGNED`
- `ACCEPTED`
- `DECLINED`
- `IN_REVIEW`
- `SUBMITTED`
- `OVERDUE`
- `REASSIGNED`
- `CANCELLED`

#### 关键转换

- `ASSIGNED -> ACCEPTED`：评审员接受任务
- `ASSIGNED -> DECLINED`：评审员拒绝任务
- `ASSIGNED -> OVERDUE`：未接受也未拒绝且超时
- `ACCEPTED -> IN_REVIEW`：开始填写评审
- `ACCEPTED -> OVERDUE`：接受但超时未提交
- `IN_REVIEW -> SUBMITTED`：提交评审
- `IN_REVIEW -> OVERDUE`：填写中超时
- `OVERDUE -> SUBMITTED`：主席允许补交
- `OVERDUE -> REASSIGNED`：主席改派
- `DECLINED -> REASSIGNED`：主席重新分配
- `ASSIGNED / ACCEPTED / IN_REVIEW -> CANCELLED`：任务撤销

### 5.4 跨模块事务边界

核心业务状态变更必须在同一事务内完成，例如主席作出决定时：

- 更新稿件状态
- 更新轮次状态
- 关闭或结束当前任务
- 写入决策记录

通知发送、失败补偿等动作为异步处理，允许最终一致，不应因通知失败导致主事务回滚。

### 5.5 并发控制

关键写操作需做状态前置校验与乐观并发控制：

- 主席决策前校验稿件仍为 `UNDER_REVIEW`
- 评审员提交前校验任务仍为 `ACCEPTED / IN_REVIEW / OVERDUE`
- 建议在 `MANUSCRIPT`、`REVIEW_ROUND`、`REVIEW_ASSIGNMENT` 增加版本号或更新时间校验

## 6. 数据库设计

### 6.1 设计原则

- 核心业务对象采用强结构化建模
- 长文本、审计细节和 Agent 结果使用 `CLOB`
- PDF 使用 `BLOB`
- Agent 结果按原始版与脱敏版双字段存储，避免重复行冗余

### 6.2 核心实体关系

- 一个用户可拥有多个角色
- 一个稿件可包含多个版本
- 一个稿件版本可关联多位作者
- 一个稿件可经历多轮评审
- 一轮评审包含多个评审任务
- 一个评审任务最多对应一份正式评审意见
- 一轮评审生成一条主席决策记录
- 一个稿件版本可对应多个 Agent 分析任务与结果

### 6.3 核心数据表

#### 6.3.1 `SYS_USER`

- `USER_ID`
- `USERNAME`
- `PASSWORD_HASH`
- `REAL_NAME`
- `EMAIL`
- `INSTITUTION`
- `STATUS`
- `CREATED_AT`

#### 6.3.2 `SYS_ROLE`

- `ROLE_ID`
- `ROLE_CODE`
- `ROLE_NAME`

#### 6.3.3 `SYS_USER_ROLE`

- `USER_ROLE_ID`
- `USER_ID`
- `ROLE_ID`

#### 6.3.4 `USER_RESEARCH_AREA`

- `USER_RESEARCH_AREA_ID`
- `USER_ID`
- `AREA_CODE`
- `AREA_NAME`

用于记录评审员研究方向，支撑后续推荐分配。

#### 6.3.5 `MANUSCRIPT`

- `MANUSCRIPT_ID`
- `SUBMITTER_ID`
- `CURRENT_VERSION_ID`
- `CURRENT_STATUS`
- `CURRENT_ROUND_NO`
- `BLIND_MODE`
- `SUBMITTED_AT`
- `LAST_DECISION_CODE`

#### 6.3.6 `MANUSCRIPT_VERSION`

- `VERSION_ID`
- `MANUSCRIPT_ID`
- `VERSION_NO`
- `VERSION_TYPE`
- `TITLE`
- `ABSTRACT`
- `KEYWORDS`
- `PDF_FILE` (`BLOB`)
- `PDF_FILE_NAME`
- `PDF_FILE_SIZE`
- `SUBMITTED_BY`
- `SUBMITTED_AT`
- `SOURCE_DECISION_ID`

#### 6.3.7 `MANUSCRIPT_AUTHOR`

- `MANUSCRIPT_AUTHOR_ID`
- `MANUSCRIPT_ID`
- `VERSION_ID`
- `USER_ID`
- `AUTHOR_NAME`
- `EMAIL`
- `INSTITUTION`
- `AUTHOR_ORDER`
- `IS_CORRESPONDING`
- `IS_EXTERNAL`

该表支持系统内作者与外部作者混合建模，并为冲突检测与双盲脱敏提供完整作者列表。

#### 6.3.8 `REVIEW_ROUND`

- `ROUND_ID`
- `MANUSCRIPT_ID`
- `ROUND_NO`
- `VERSION_ID`
- `ROUND_STATUS`
- `ASSIGNMENT_STRATEGY`
- `SCREENING_REQUIRED`
- `DEADLINE_AT`
- `CREATED_BY`
- `CREATED_AT`

`ROUND_STATUS` 枚举：

- `PENDING`
- `IN_PROGRESS`
- `COMPLETED`
- `CANCELLED`

#### 6.3.9 `REVIEW_ASSIGNMENT`

- `ASSIGNMENT_ID`
- `ROUND_ID`
- `MANUSCRIPT_ID`
- `VERSION_ID`
- `REVIEWER_ID`
- `TASK_STATUS`
- `ASSIGNED_AT`
- `ACCEPTED_AT`
- `DECLINED_AT`
- `DECLINE_REASON`
- `DEADLINE_AT`
- `SUBMITTED_AT`
- `REASSIGNED_FROM_ID`

说明：

- `MANUSCRIPT_ID` 和 `VERSION_ID` 为刻意冗余，用于减少高频查询中的多表关联

#### 6.3.10 `CONFLICT_CHECK_RECORD`

- `CONFLICT_ID`
- `ASSIGNMENT_ID`
- `MANUSCRIPT_ID`
- `REVIEWER_ID`
- `CONFLICT_TYPE`
- `CONFLICT_DESC`
- `SOURCE`
- `DECLARED_BY`
- `DECLARED_AT`
- `DETECTED_AT`
- `CONFIRMED_BY_CHAIR`

`SOURCE` 可取：

- `SYSTEM_DETECTED`
- `SELF_DECLARED`

#### 6.3.11 `REVIEW_REPORT`

- `REVIEW_ID`
- `ASSIGNMENT_ID`
- `ROUND_ID`
- `MANUSCRIPT_ID`
- `REVIEWER_ID`
- `NOVELTY_SCORE`
- `METHOD_SCORE`
- `EXPERIMENT_SCORE`
- `WRITING_SCORE`
- `OVERALL_SCORE`
- `CONFIDENCE_LEVEL`
- `STRENGTHS` (`CLOB`)
- `WEAKNESSES` (`CLOB`)
- `COMMENTS_TO_AUTHOR` (`CLOB`)
- `COMMENTS_TO_CHAIR` (`CLOB`)
- `RECOMMENDATION`
- `SUBMITTED_AT`

评分统一采用 `1-5` 整数分值。

#### 6.3.12 `DECISION_RECORD`

- `DECISION_ID`
- `MANUSCRIPT_ID`
- `ROUND_ID`
- `VERSION_ID`
- `DECISION_CODE`
- `DECISION_REASON`
- `DECIDED_BY`
- `DECIDED_AT`

`DECISION_CODE` 可取：

- `ACCEPT`
- `REJECT`
- `MINOR_REVISION`
- `MAJOR_REVISION`
- `DESK_REJECT`

#### 6.3.13 `AGENT_ANALYSIS_TASK`

- `TASK_ID`
- `MANUSCRIPT_ID`
- `VERSION_ID`
- `ROUND_ID`
- `TASK_TYPE`
- `TASK_STATUS`
- `REQUEST_PAYLOAD` (`CLOB`)
- `RESULT_SUMMARY` (`CLOB`)
- `EXTERNAL_TASK_ID`
- `RETRY_COUNT`
- `CREATED_AT`
- `FINISHED_AT`

#### 6.3.14 `AGENT_ANALYSIS_RESULT`

- `RESULT_ID`
- `TASK_ID`
- `MANUSCRIPT_ID`
- `VERSION_ID`
- `RESULT_TYPE`
- `RAW_RESULT_JSON` (`CLOB`)
- `REDACTED_RESULT_JSON` (`CLOB`)
- `CREATED_AT`

#### 6.3.15 `AGENT_FEEDBACK`

- `FEEDBACK_ID`
- `TASK_ID`
- `RESULT_TYPE`
- `USER_ID`
- `RATING`
- `FEEDBACK_COMMENT`
- `CREATED_AT`

#### 6.3.16 `SYS_NOTIFICATION`

- `NOTIFICATION_ID`
- `RECEIVER_ID`
- `BIZ_TYPE`
- `BIZ_ID`
- `TITLE`
- `CONTENT`
- `IS_READ`
- `CREATED_AT`

#### 6.3.17 `AUDIT_LOG`

- `LOG_ID`
- `OPERATOR_ID`
- `OPERATION_TYPE`
- `BIZ_TYPE`
- `BIZ_ID`
- `DETAIL_JSON` (`CLOB`)
- `CREATED_AT`

### 6.4 Oracle 特性使用

- `SEQUENCE`：主键生成
- `BLOB`：论文 PDF 存储
- `CLOB`：长文本、JSON、审计详情
- `TRIGGER`：审计字段维护、日志留痕
- `PROCEDURE`：统计报表、评审完成率、工作量汇总

说明：PDF 存入 BLOB 主要为满足课程设计要求，真实生产环境通常更适合对象存储。

## 7. 关键时序设计

### 7.1 投稿与初筛流程

1. 作者创建稿件与版本记录
2. 作者上传 PDF
3. 稿件状态变为 `SUBMITTED`
4. 系统通知主席
5. 主席点击开始筛查，稿件状态变为 `UNDER_SCREENING`
6. 若初筛不通过，状态变为 `DESK_REJECTED`
7. 若初筛通过，创建首轮评审并进入 `UNDER_REVIEW`

### 7.2 评审分配与接受/拒绝流程

1. 主席选择评审员
2. 系统基于作者列表和评审员信息执行冲突检测
3. 创建评审任务，状态为 `ASSIGNED`
4. 系统通知评审员
5. 评审员可执行：
   - 接受任务
   - 拒绝任务
   - 声明利益冲突
6. 若拒绝或声明冲突，系统通知主席
7. 主席重新分配，新评审员收到通知

### 7.3 Agent 异步分析流程

系统定义三类 `TASK_TYPE`：

- `SCREENING_ANALYSIS`
- `REVIEW_ASSIST_ANALYSIS`
- `DECISION_CONFLICT_ANALYSIS`

#### 触发时机

- `SCREENING_ANALYSIS`：`SUBMITTED -> UNDER_SCREENING` 后触发
- `REVIEW_ASSIST_ANALYSIS`：稿件进入 `UNDER_REVIEW` 且评审任务创建完成后触发
- `DECISION_CONFLICT_ANALYSIS`：当前轮评审意见全部提交后，或主席主动触发

#### 流程

1. 主系统创建 Agent 任务
2. 主系统读取 PDF BLOB
3. 主系统以 multipart 请求提交任务与 PDF
4. Agent Service 返回任务编号
5. 主系统按退避策略轮询状态
6. Agent Service 完成后返回结果
7. 主系统二次校验、写入原始与脱敏结果
8. 系统通知评审员与主席

### 7.4 评审提交与主席决策流程

1. 评审员查看匿名论文和脱敏 Agent 结果
2. 评审员提交结构化评审
3. 任务状态变为 `SUBMITTED`
4. 系统通知主席
5. 主席查看评审意见和 Agent 汇总
6. 主席提交决定
7. 同一事务中完成状态更新、任务收尾和决策记录写入
8. 事务提交后异步通知作者

### 7.5 修改后重审流程

1. 主席作出 `MINOR_REVISION / MAJOR_REVISION` 决策
2. 稿件状态变为 `REVISION_REQUIRED`
3. 作者提交修改稿
4. 系统创建新版本，状态变为 `REVISED_SUBMITTED`
5. 判断是否需要重新筛查
6. 若需要，则进入 `UNDER_SCREENING`
7. 筛查通过后，根据 `ASSIGNMENT_STRATEGY` 决定沿用评审员或重新分配
8. 创建新一轮 `REVIEW_ROUND`
9. 重新触发 Agent 分析

### 7.6 超时与补交流程

主系统通过 `Spring @Scheduled` 定时任务处理：

- 评审超时检测
- Agent 任务状态轮询
- 失败通知补偿
- 待重试任务重投

超时后：

- 任务状态变为 `OVERDUE`
- 系统通知评审员与主席
- 主席可允许补交、重新分配或取消任务

## 8. Agent 架构与工具调用设计

### 8.1 总体设计

Agent Service 采用：

- `FastAPI`：任务接收与状态查询接口
- `Task Router`：根据 `TASK_TYPE` 选择 workflow 的普通 Python 路由逻辑
- `LangGraph`：承载工作流状态图
- `Coordinator Agent`：LLM 驱动的编排节点
- `Specialist Agents`：专项分析节点

### 8.2 组件定位

#### 8.2.1 Task Router

不是 Agent，不做 LLM 推理，仅负责：

- 校验任务类型
- 检查缓存
- 选择对应 LangGraph workflow

#### 8.2.2 Coordinator Agent

是真正的 LLM Agent，负责：

- 读取任务上下文
- 决定调用哪些 Specialist Agent
- 决定调用顺序
- 判断结果是否足够完成任务
- 汇总输出

#### 8.2.3 Specialist Agents

- `Paper Understanding Agent`
- `Novelty Analysis Agent`
- `Methodology Analysis Agent`
- `Writing Quality Agent`
- `Conflict Summary Agent`

### 8.3 Coordinator Agent 约束

为避免自由发挥，必须限制：

- `SCREENING_ANALYSIS` 最多 2 轮 Agent 调用
- `REVIEW_ASSIST_ANALYSIS` 最多 4 轮 Agent 调用
- `DECISION_CONFLICT_ANALYSIS` 最多 3 轮 Agent 调用
- 必须先完成基础理解，再进入专项分析
- 结果不完整时最多补救 1 次
- 最终输出必须通过 schema 校验

### 8.4 Paper Understanding Agent 的中间表示

```json
{
  "manuscriptId": "string",
  "versionId": "string",
  "title": "string",
  "abstractSummary": "string",
  "keywords": ["string"],
  "researchProblem": "string",
  "claimedContributions": ["string"],
  "methodSummary": "string",
  "experimentSummary": "string",
  "mainResults": ["string"],
  "limitationsMentioned": ["string"],
  "citationSignals": ["string"],
  "possibleBlindnessRisks": ["string"],
  "sections": {
    "introduction": "string",
    "method": "string",
    "experiment": "string",
    "conclusion": "string"
  }
}
```

该中间表示作为后续 Specialist Agents 的统一输入接口。

### 8.5 工具层设计

Agent 可调用工具包括：

- `PdfExtractTool`
- `SectionSplitTool`
- `MetadataFetchTool`
- `ReviewTemplateTool`
- `ConflictCompareTool`
- `RedactionTool`
- `ResultValidationTool`

缓存检查不作为 Agent 工具，而在 `Task Router` 中处理。

### 8.6 按任务类型划分的 Agent 工作流

#### 8.6.1 `SCREENING_ANALYSIS`

默认使用：

- `Paper Understanding Agent`

按需可调用：

- `Writing Quality Agent`

输出内容：

- 主题归类
- 领域匹配度
- 格式风险
- 双盲风险提示
- 初筛摘要

#### 8.6.2 `REVIEW_ASSIST_ANALYSIS`

使用：

- `Paper Understanding Agent`
- `Novelty Analysis Agent`
- `Methodology Analysis Agent`
- `Writing Quality Agent`

输出内容：

- 论文概要
- 创新性分析
- 方法评估
- 写作质量分析
- 风险提示
- 总体建议

#### 8.6.3 `DECISION_CONFLICT_ANALYSIS`

使用：

- `Conflict Summary Agent`

输入：

- 多份正式评审报告
- 评分项
- 评审员置信度
- 历史 Agent 分析摘要

输出：

- 共识点
- 冲突点
- 高风险问题
- 决策参考摘要

### 8.7 按任务类型划分的输出 schema

#### 通用字段

```json
{
  "taskType": "string",
  "manuscriptId": "string",
  "versionId": "string",
  "status": "string",
  "confidence": "number"
}
```

#### `SCREENING_ANALYSIS`

```json
{
  "taskType": "SCREENING_ANALYSIS",
  "manuscriptId": "string",
  "versionId": "string",
  "topicCategory": "string",
  "scopeFit": "FIT|PARTIAL|UNFIT",
  "formatRisks": ["string"],
  "blindnessRisks": ["string"],
  "screeningSummary": "string",
  "confidence": "number"
}
```

#### `REVIEW_ASSIST_ANALYSIS`

```json
{
  "taskType": "REVIEW_ASSIST_ANALYSIS",
  "manuscriptId": "string",
  "versionId": "string",
  "summary": "string",
  "novelty": {
    "analysis": "string",
    "score": "integer(1-5)"
  },
  "methodology": {
    "analysis": "string",
    "score": "integer(1-5)"
  },
  "writing": {
    "analysis": "string",
    "score": "integer(1-5)"
  },
  "risks": ["string"],
  "finalSuggestion": "string",
  "confidence": "number"
}
```

其中评分字段统一采用 `1-5` 的整数分值，与 `REVIEW_REPORT` 中的评分量级保持一致。

#### `DECISION_CONFLICT_ANALYSIS`

```json
{
  "taskType": "DECISION_CONFLICT_ANALYSIS",
  "manuscriptId": "string",
  "versionId": "string",
  "consensusPoints": ["string"],
  "conflictPoints": ["string"],
  "highRiskIssues": ["string"],
  "decisionSummary": "string",
  "confidence": "number"
}
```

### 8.8 脱敏与质量校验

治理动作分散在流程中执行：

- 任务接收时：任务类型校验、缓存检查、去重
- 编排过程中：调用轮次限制、成本控制
- 结果汇总后：调用 `RedactionTool`
- 输出前：调用 `ResultValidationTool`
- 主系统入库前：做二次校验与角色权限控制

双盲脱敏重点处理：

- 自引中暴露作者身份的表述
- 致谢中的机构和项目名
- 作者前期工作的身份线索
- 元数据中的机构和作者信息

### 8.9 容错与成本控制

- 同一稿件版本优先命中缓存
- 不同任务类型可配置不同模型
- 使用有限并发控制 Agent 同时运行数量
- LLM 限流时退避重试
- 任务失败时主流程继续

### 8.10 人工反馈闭环

- 评审员可标记“有帮助/无帮助”
- 主席可标记“准确/不准确”
- 反馈存入 `AGENT_FEEDBACK`
- 用于后续 prompt 优化与策略调整

## 9. 接口设计与非功能需求

### 9.1 主系统业务接口

#### 投稿与版本管理

- `POST /api/manuscripts`
- `GET /api/manuscripts/{id}`
- `POST /api/manuscripts/{id}/versions`
- `GET /api/manuscripts/{id}/versions`
- `POST /api/manuscripts/{id}/versions/{versionId}/pdf`

#### 初筛与轮次管理

- `POST /api/manuscripts/{id}/screening/start`
- `POST /api/manuscripts/{id}/screening/decision`
- `POST /api/review-rounds`
- `GET /api/review-rounds/{id}`
- `POST /api/review-rounds/{roundId}/conflict-analysis`
- `GET /api/review-rounds/{roundId}/conflict-checks?reviewerId=xxx&manuscriptId=xxx`

#### 分配与任务

- `POST /api/review-assignments`
- `POST /api/review-assignments/{id}/accept`
- `POST /api/review-assignments/{id}/decline`
- `POST /api/review-assignments/{id}/conflict-declare`
- `POST /api/review-assignments/{id}/reassign`

#### 评审与决策

- `POST /api/review-reports`
- `GET /api/manuscripts/{id}/reviews`
- `POST /api/decisions`

#### Agent 结果查询

- `GET /api/manuscripts/{id}/versions/{versionId}/agent-results`
- `POST /api/agent-results/{resultId}/feedback`
- `GET /api/manuscripts/{id}/agent-feedback`

主系统根据角色返回：

- 主席：`RAW_RESULT_JSON`
- 评审员：`REDACTED_RESULT_JSON`

#### 通知与日志

- `GET /api/notifications`
- `POST /api/notifications/{id}/read`
- `GET /api/audit-logs`

### 9.2 Agent Service 集成接口

#### 提交任务

- `POST /agent/tasks`

采用 `multipart/form-data`：

- `metadata`：JSON 字符串
- `file`：PDF 二进制

#### 查询任务状态

- `GET /agent/tasks/{taskId}`

返回字段建议：

- `taskId`
- `status`
- `step`

`step` 可取：

- `queued`
- `parsing`
- `understanding`
- `analyzing`
- `summarizing`
- `validating`
- `completed`
- `failed`

#### 获取任务结果

- `GET /agent/tasks/{taskId}/result`

### 9.3 分页与筛选要求

以下列表接口统一支持分页与筛选：

- `GET /api/manuscripts`
- `GET /api/notifications`
- `GET /api/audit-logs`
- `GET /api/review-assignments`
- `GET /api/review-rounds`

建议参数：

- `page`
- `size`
- `status`
- `keyword`
- `roundId`
- `manuscriptId`

### 9.4 认证与安全

#### 主系统认证

- 使用 `Spring Security + JWT`

#### 服务间认证

- 主系统调用 Agent Service 使用内部 `API Key` 或服务间 token

#### 基础安全措施

- 参数绑定防 SQL 注入
- 前端输出转义防 XSS
- 文件上传限制 PDF 类型、大小和 MIME type
- 控制请求大小上限
- 双盲视图严格按角色返回

### 9.5 性能与可用性目标

- 普通查询响应时间小于 `2s`
- 一般写操作响应时间小于 `3s`
- Agent 任务创建响应时间小于 `1s`
- Agent 分析允许异步长耗时执行
- 支持至少 `100` 个并发在线用户的课程演示级负载

### 9.6 一致性要求

- 状态机必须严格约束写操作
- 决策相关核心动作需在单事务内完成
- 通知与反馈允许最终一致
- Agent 结果入库前必须通过二次校验

## 10. 部署设计与技术实现建议

### 10.1 部署结构

建议部署为四个组件：

- 前端：`Nginx + Vue 3`
- 主系统：`Spring Boot`
- Agent Service：`FastAPI + LangGraph`
- 数据库：`Oracle`

### 10.2 部署关系

1. 浏览器访问前端页面
2. 前端调用主系统 API
3. 主系统访问 Oracle
4. 主系统调用 Agent Service
5. Agent Service 返回分析结果
6. 主系统入库并对前端提供查询

### 10.3 主系统实现重点

- 状态机实现
- 事务与通知解耦
- 双盲权限控制
- Agent 结果角色视图切换
- 定时任务处理超时与补偿

### 10.4 Agent Service 实现重点

- Workflow Registry
- Coordinator Agent 提示词与约束
- Specialist Agents
- 结果校验与脱敏
- 任务状态维护

### 10.5 Agent 任务状态持久化

课程项目可采用 Agent Service 内存任务表保存：

- `taskId`
- `status`
- `step`
- `error`

由于内存态在服务重启后会丢失，主系统需配合超时检测：

- 若轮询超过设定时限仍未完成，则主系统主动将任务标记为 `FAILED`
- 课程项目建议将该超时阈值设置为 `10 分钟`

### 10.6 LLM 提供商与模型配置

Agent Service 应将以下内容配置化，不得硬编码：

- `LLM_PROVIDER`
- `LLM_MODEL_SCREENING`
- `LLM_MODEL_REVIEW`
- `LLM_MODEL_CONFLICT`
- `LLM_API_KEY`

### 10.7 并发任务处理策略

课程项目建议采用有限并发处理，例如通过 `asyncio + semaphore` 限制同时运行 `2-3` 个任务，避免触发速率限制并控制成本。

### 10.8 定时任务与补偿

主系统使用 `Spring @Scheduled` 处理：

- 评审超时检查
- Agent 任务状态轮询
- 失败通知重试
- 待重试分析任务重投

### 10.9 CORS 配置

当前端与主系统不同源时，需要在主系统中配置 CORS，允许前端域名访问业务 API。Agent Service 不对浏览器开放，无需暴露前端跨域访问。

## 11. 课程答辩展示重点与创新点

### 11.1 项目定位

本项目是一个“以论文评审流程为核心、以 Agent 作为智能辅助能力”的管理信息系统，而不是单纯的 CRUD 系统或 AI 替代人工系统。

### 11.2 主要亮点

- 主系统与 Agent Service 解耦
- 多轮评审与版本管理闭环完整
- 双盲场景下的 Agent 结果脱敏机制
- Agent 异步集成与失败降级
- Oracle 特性使用场景明确自然

### 11.3 为什么论文评审系统适合 Agent

- 论文是长文本和非结构化内容
- 评审需要多维度综合分析
- 多位评审之间存在意见冲突
- 输出既要结构化也要自然语言说明

### 11.4 为什么 Agent 不能替代人工

- 学术评审属于高风险决策
- LLM 存在幻觉和偏差风险
- 双盲与公平性要求高
- 因此 Agent 仅做辅助分析，正式意见与最终决策必须由人工完成

### 11.5 与课程评分点的对应关系

- `系统设计合理`：分层架构、模块划分、状态机、时序流程
- `数据库设计合理`：Oracle 表结构、BLOB/CLOB、轮次与版本建模
- `功能完整`：投稿、初筛、分配、评审、决策、重审、通知闭环
- `创新点明确`：Agent Service、多 Agent 分析、双盲脱敏、冲突总结
- `技术实现可行`：Spring Boot + Oracle + FastAPI/LangGraph 的分工明确

## 12. 总结

本系统以传统论文评审管理流程为主线，以独立 Agent Service 提供论文理解、评审辅助和冲突分析能力，在保证 Oracle 数据设计完整性、状态机一致性和双盲评审规范性的前提下，实现了一个可解释、可扩展、可答辩展示的智能论文评审系统。

该设计既满足课程项目对数据库与软件工程规范的要求，也体现了 Agent 技术在真实复杂业务场景中的合理落点。
