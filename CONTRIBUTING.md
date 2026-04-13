# Contributing Guide

感谢你为 PaperTide Review 做贡献。为确保协作效率与可维护性，请遵循以下规范。

## 分支与提交

- 从 `main` 拉取最新代码后创建功能分支
- 提交信息使用祈使句，可选 scope
- 示例：`feat(web): add reviewer task filters`

## 代码规范

- Java/Python 使用 4 空格缩进
- Vue/JSON/CSS 使用 2 空格缩进
- 保持 import 整洁，避免无关重构
- 新增逻辑必须包含对应测试

## 本地验证（提交前必须）

优先执行：

```bash
bash scripts/test-all.sh
```

若仅改动单个服务，可执行对应命令：

- API：`cd apps/api && mvn test`
- Agent：`cd services/agent && python3 -m pytest tests/`
- Web：`cd apps/web && npm run test -- --run && npm run typecheck && npm run build`

## Pull Request 要求

- 说明本次改动背景与目标
- 列出核心改动点
- 附上本地验证命令与结果
- 涉及 UI 变更时附截图
- 避免超大 PR，优先小步迭代

## 安全与配置

- 不要提交真实密钥、令牌和 `.env`
- 不要提交本地 IDE、缓存与构建产物
- 涉及 Oracle schema 变更时，保持 SQL 顺序并更新验证脚本

## 建议协作流程

1. 先读 `README.md` 和 `TODO.md`
2. 认领一个 P0/P1 问题
3. 小步提交并附测试
4. 通过评审后再继续下一项
