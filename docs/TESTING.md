# 当前测试说明

## 1. 测试目标

- 验证核心业务链路可用性
- 验证角色权限与关键状态转换
- 验证 Agent 路由与返回结构稳定
- 验证前端关键页面与路由守卫行为

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

### 2.2 Agent（pytest）

位置：`services/agent/tests`

当前测试文件：

- `test_health.py`
- `test_tasks_api.py`
- `test_multipart_tasks_api.py`
- `test_workflow_schemas.py`

### 2.3 Web（Vitest + jsdom + vue-tsc）

位置：`apps/web/src`

当前测试文件：

- `App.spec.ts`
- `tests/login.spec.ts`
- `tests/workflow.spec.ts`

类型检查与构建验证：

- `npm run typecheck`
- `npm run build`

## 3. 统一测试入口

推荐命令：

```bash
bash scripts/test-all.sh
```

该脚本会：

1. 尝试执行 Oracle 演示种子脚本
2. 运行 API 测试
3. 运行 Agent 测试（当前脚本为 health 子集）
4. 运行 Web 测试 + typecheck + build

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

Web：

```bash
cd apps/web
npm run test -- --run
npm run typecheck
npm run build
```

## 5. 质量门槛建议

- 新功能至少新增一个同层级自动化测试
- 修复缺陷必须补回归测试
- 涉及权限和状态机改动时，补充 e2e 或集成测试
