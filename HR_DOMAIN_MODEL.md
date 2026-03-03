# HR 执行助手领域模型与状态机（MVP）

## 模型

### CandidateCase
- 字段：`caseId, candidateName, position, stage, owner, riskLevel, nextAction, dueAt, status, createdAt, updatedAt`
- 含义：候选人事务主对象，承载阶段、风险与下一动作。

### ActionItem
- 字段：`id, caseId, actionType, title, dueAt, priority, status, sourceEvidence, createdAt, updatedAt`
- 含义：从 Case 拆解出的可执行动作。

### EvidenceRef
- 字段：`id, caseId, sourceType, sourcePathOrUrl, snippet, confidence, createdAt`
- 含义：候选人结论的证据引用，支持可追溯。

### ReminderEvent
- 字段：`id, actionId, remindAt, channel, status, createdAt`
- 含义：动作提醒事件，首发默认 in-app。

## 状态机

### CandidateCase.stage
- `SOURCING -> SCREENING -> INTERVIEW -> OFFER -> ONBOARDING -> CLOSED`
- 支持回退（例如 `INTERVIEW -> SCREENING`）用于候选人补评估。

### CandidateCase.status
- `OPEN`: 正常推进
- `BLOCKED`: 有阻塞因素（待补材料、候选人待回复等）
- `CLOSED`: 流程结束

### ActionItem.status
- `TODO -> IN_PROGRESS -> DONE`
- 任意中间态可进入 `CANCELLED`

## 持久化策略

- 本地文件存储（`~/.everything-assistant/`）：
  - `hr-cases.tsv`
  - `hr-actions.tsv`
  - `hr-evidence.tsv`
  - `hr-reminders.tsv`
- 目标：MVP 先稳定闭环，后续再演进到数据库或外部系统。

