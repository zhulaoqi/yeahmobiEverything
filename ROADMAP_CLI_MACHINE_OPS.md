# CLI 能力接入落地清单

## 已交付范围

- 本机执行网关（客户端进程）：
  - `GET /api/local-cli/os`
  - `POST /api/local-cli/exec`
  - `POST /api/local-cli/schedule/create`
  - `GET /api/local-cli/schedule/list`
  - `POST /api/local-cli/schedule/pause`
  - `POST /api/local-cli/schedule/delete`
  - `POST /api/local-cli/schedule/run-now`
- AgentScope 新工具：`CliBridgeTool`
- ToolRegistry 新增 `os-cli` / `os-scheduler` / `machine-ops` 解析
- 系统提示词新增 CLI 执行约束（先检测 OS、先 dry-run、风险确认）
- 新增机器操作域能力：
  - 命令风控：`CommandPolicyEngine`
  - 命令执行：`DefaultOsCommandAdapter`
  - 调度：`NativeOsSchedulerAdapter`（当前稳定支持 windows 原生）
  - 回退调度：`AppInternalScheduler`
  - 审计：`CliAuditStore`
- 前端可视化：
  - 聊天中命令卡片：预执行 / 执行 / 复制
  - CLI 定时任务面板：创建、刷新、暂停、删除、立即执行

## 风险控制规则

- 禁止 `sudo` 提权执行
- 高危命令黑名单直接拒绝（如格式化、破坏系统命令）
- 中风险命令默认要求确认
- 执行超时限制：1~300 秒
- 输出内容截断，避免超长日志影响 UI
- 本地网关支持 `X-Local-Token`（配置 `local.cli.gateway.token`）

## 配置项

在 `everything-server/src/main/resources/application.properties`：

- `local.cli.gateway.port=19199`
- `local.cli.gateway.url=http://127.0.0.1:19199`
- `local.cli.gateway.token=`

## 验证清单

1. 编译验证：
   - `./mvnw -DskipTests clean compile`
2. 基础执行：
   - 在 CLI skill 中请求 `detect os`，确认返回系统类型
   - 发送低风险命令（如 `pwd` / `dir` / `ls`），先 dry-run，再执行
3. 风险验证：
   - 输入黑名单命令，确认被拒绝
   - 输入中风险命令，确认提示需确认
4. 定时任务：
   - 创建 `every:1m` 任务，确认列表可见
   - 执行 `run-now`，确认状态变化
   - 暂停 / 删除，确认状态同步
5. 审计：
   - 检查 `~/.everything-assistant/cli-audit.log` 是否写入记录

## 下一步建议

- 补齐 macOS / Linux 原生调度实现（launchd / cron/systemd）
- 命令白名单目录策略（按用户工作区配置）
- 增加“确认执行”交互态（二次弹窗而非仅按钮触发）
