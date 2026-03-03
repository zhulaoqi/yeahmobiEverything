# HR 意图路由与非技术响应编排规范

## 1) 意图路由

- 路由枚举：
  - `RECRUITMENT_ADVANCE`
  - `INTERVIEW_DECISION`
  - `OFFER_ONBOARDING`
  - `HR_TRANSACTION`
  - `GENERAL`

- 路由输入：用户问题正文（含附件文本）。
- 路由输出：意图类型 + 推荐动作模板。

## 2) 响应编排（三段式）

- 固定结构：
  - `【我将帮你做什么】`
  - `【请确认】`
  - `【结果】`

- 约束：
  - 不输出教程化步骤。
  - 不暴露技术参数。
  - 有结论时给证据摘要。
  - 信息缺失时给默认动作并提示补齐。

## 3) 与现有系统结合

- Agent 规则层：通过 `AgentScopeServer` 的 HR guide 强制三段式约束。
- Skill 强化层：`AdminServiceImpl` 自动为 HR 技能注入执行规范。
- 输出模板层：`NonTechResponseComposer` 提供标准文案模板。

