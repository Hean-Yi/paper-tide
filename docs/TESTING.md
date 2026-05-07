# 当前测试说明

## 1. 测试目标

- 验证核心业务链路可用性
- 验证角色权限与关键状态转换
- 验证 analysis / execution 平台状态流转与结果结构稳定
- 验证前端关键页面与路由守卫行为
- 验证会议 CFP、注册审批、bidding、assignment draft、Agent assignment assist 的集成链路
- 验证极端输入下 Agent provider prompt 预算和批量执行隔离

## 2. 测试分层

### 2.1 API（JUnit 5 + Spring Boot Test）

位置：`apps/api/src/test/java`

当前测试类：

- `AuthControllerTest`
- `HealthControllerTest`
- `ManuscriptServiceTest`
- `ReviewWorkflowServiceTest`
- `DecisionServiceTest`
- `WorkflowQueryServiceTest`
- `AgentIntegrationServiceTest`
- `ReviewFlowE2eTest`
- `RegistrationSchemaTest`
- `ConferenceSchemaTest`
- `ConferenceReviewerBiddingServiceTest`
- `AssignmentDraftServiceTest`
- `RequestReviewerAssignmentAssistUseCaseTest`

### 2.2 Agent（pytest）

位置：`services/agent/tests`

当前测试文件：

- `test_health.py`
- `test_execution_job.py`
- `test_execution_runtime.py`
- `test_message_consumer.py`
- `test_reviewer_assist_flow.py`
- `test_screening_flow.py`
- `test_conflict_analysis_flow.py`
- `test_workflow_schemas.py`
- `test_provider_executor.py`

重点 Agent 检查：

- `test_provider_executor_limits_large_assignment_candidate_payload_before_prompting`：150 个候选和长 rationale 不应生成超大 prompt
- `test_runtime_processes_many_assignment_assist_requests_without_cross_polluting_events`：60 个 assignment-assist 请求应生成隔离的完成事件
- `test_runtime_marks_provider_failure_retryable_instead_of_leaving_job_running`：provider 异常不能让 job 长期停在 `RUNNING`

### 2.3 Web（Vitest + jsdom + vue-tsc）

位置：`apps/web/src`

当前测试文件：

- `App.spec.ts`
- `tests/agent-projection.spec.ts`
- `tests/conference-workflow.spec.ts`
- `tests/login.spec.ts`
- `tests/registration.spec.ts`
- `tests/workflow.spec.ts`
- `tests/setup.ts`

类型检查与构建验证：

- `npm run typecheck`
- `npm run build`

## 3. 统一测试入口

推荐命令：

```bash
bash scripts/test-all.sh
```

该脚本会：

1. 可选启动本地 RabbitMQ
2. 尝试执行 Oracle 演示种子脚本
3. 运行 API 测试
4. 运行完整 Agent pytest；若缺少 Python 依赖则退化为语法校验
5. 运行 Web 测试 + typecheck + build；若缺少 `node_modules` 则退化为最小文件存在性检查

## 4. 单服务调试命令

API：

```bash
cd apps/api
mvn test
```

Agent：

```bash
cd services/agent
python3 -m pytest tests/
```

Agent 重点压力/极端场景：

```bash
./.venv/bin/python -m pytest services/agent/tests/test_provider_executor.py services/agent/tests/test_execution_runtime.py -q
```

如果使用仓库虚拟环境：

```bash
./.venv/bin/python -m pytest services/agent/tests/
```

Web：

```bash
cd apps/web
npm run test -- --run
npm run typecheck
npm run build
```

Conference 前端流程：

```bash
cd apps/web
npm run test -- --run src/tests/conference-workflow.spec.ts
```

## 5. 质量门槛建议

- 新功能至少新增一个同层级自动化测试
- 修复缺陷必须补回归测试
- 涉及权限和状态机改动时，补充 e2e 或集成测试
- 涉及 analysis 平台改动时，优先补 execution job、message consumer 或对应 flow 测试，而不是只测 HTTP 外壳
