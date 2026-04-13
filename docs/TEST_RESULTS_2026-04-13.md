# 测试结果快照（2026-04-13）

## 1. 执行环境与入口

- 执行时间：2026-04-13（本地）
- 统一命令：`bash scripts/test-all.sh`
- 仓库路径：`/Users/hean/Agent_proj`

## 2. API 测试结果

- 命令：脚本内执行 `cd apps/api && mvn test`
- 结果：`BUILD SUCCESS`
- 汇总：`Tests run: 41, Failures: 0, Errors: 0, Skipped: 0`

已执行测试类（摘要）：

- `ManuscriptServiceTest`
- `AuthControllerTest`
- `HealthControllerTest`
- `AgentIntegrationServiceTest`
- `ReviewWorkflowServiceTest`
- `WorkflowQueryServiceTest`
- `DecisionServiceTest`
- `ReviewFlowE2eTest`

## 3. Agent 测试结果

- 命令：脚本内执行 `cd services/agent && python -m pytest tests/test_health.py`
- 结果：`1 passed, 1 warning`
- 说明：当前 `test-all.sh` 默认只跑 health 子集，不是全量 Agent 测试

警告摘要：

- Python 3.14 下 LangChain Core 的 Pydantic v1 兼容性警告

## 4. Web 测试结果

- 单测命令：`npm run test -- --run`
- 结果：`Test Files 3 passed (3)`，`Tests 13 passed (13)`

类型检查：

- 命令：`npm run typecheck`
- 结果：通过

构建验证：

- 命令：`npm run build`
- 结果：通过
- 备注：出现 chunk size > 500k 的构建告警（非失败）

## 5. 结论

- 当前主链路测试在本次执行中全部通过
- 系统达到可继续迭代状态
- 后续建议：将 Agent 全量测试纳入 `test-all.sh` 默认路径
