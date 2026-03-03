# Assistant Skills Roadmap (Phase 2 -> 1 -> 3)

This roadmap aligns with the current architecture:

- Client: `everything-client` (JavaFX UI, chat, market, admin)
- Core service: `everything-server` (skill/chat/knowledge/repositories)
- Agent execution: `everything-agentscope-server` (ReAct + ToolRegistry)

## Goal

Build three business-friendly skills with shared platform capabilities:

1. Phase 2 first: file/table/screenshot understanding assistant
2. Phase 1 next: information retrieval assistant (search + citations)
3. Phase 3 then: personal follow-up assistant (todo/reminder/status)

## Shared platform layer (implement once, reuse across all phases)

1. **Context augmentation pipeline**
   - User input + attachments + retrieval evidence + task state
   - Injected before AgentScope request
2. **Structured result contract**
   - `summary`, `evidence`, `next_actions`, `artifacts`
3. **Actionable export contract**
   - Always include file path (if generated), title, sections
4. **Skill profile metadata**
   - Use `toolIds`, `toolGroups`, `contextPolicy`, `executionMode`
5. **Observable run logs**
   - Inputs, selected tools, output status, errors

---

## Phase 2: File/Table/Screenshot Understanding (MVP now)

### User value

- Drop files and ask questions in natural language.
- Assistant extracts useful context and returns actionable conclusions.

### Current MVP delivered in this iteration

- Real attachment flow in chat (multi-file selection).
- Attachment context is injected into request, no longer filename-only.
- Supported extraction now:
  - text-like files: txt/md/csv/json/log/xml/yaml/yml/java/py/js/ts/sql
  - image files: metadata note (OCR deferred)
- User message history keeps concise attachment labels.

### Next tasks

1. Add OCR tool for screenshots/images.
2. Add xlsx/docx/pdf parser toolchain.
3. Add "attachment panel" UI with remove/reorder preview.
4. Add per-attachment parsing diagnostics.

---

## Phase 1: Information Retrieval Assistant (next)

### User value

- "Help me find trustworthy answers" with citations and links.

### Implementation strategy

1. Add web search tool (site filter supported, e.g. `site:zhihu.com`).
2. Add web fetch tool for public pages.
3. Add evidence-ranking stage before final answer.
4. Enforce citation format in skill prompt.

### Output format

- conclusion (first)
- evidence bullets with URL
- confidence and missing-info note
- next actions

---

## Phase 3: Personal Follow-up Assistant (then)

### User value

- Turn natural-language intent into pending tasks and reminders.

### Implementation strategy

1. Introduce task model (`todo`, `due_at`, `status`, `channel`).
2. Add lightweight scheduler (client-trigger MVP, server scheduler later).
3. Add pause/resume/stop controls from chat and settings page.
4. Add daily digest view in app.

---

## Architecture compatibility notes

- Keep old skills unchanged; new capability is additive.
- Reuse existing `ChatService -> AgentScope -> ToolRegistry` chain.
- Avoid hard dependency on enterprise APIs in early phases.
- Everything should still work in offline/local-first mode where possible.
