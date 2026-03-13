# Backend Code Quality Refactoring — Design Spec

**Date:** 2026-03-13
**Scope:** `everything-server` module (131 Java files)
**Goal:** Improve readability and maintainability by applying three coding standards uniformly.

---

## Background

A scan of the `everything-server` module identified the following systematic issues:

- 11 `System.out.println` / `System.err.println` calls in non-Runner production code (2 more in Runner files are intentional)
- 14 classes still using `java.util.logging.Logger` instead of SLF4J (83 log call sites)
- 5+ silently swallowed exceptions (empty or log-less catch blocks)
- Magic numbers and strings scattered across service and utility classes
- Some long methods mixing IO, business logic, and formatting in a single body

No static analysis plugins (Checkstyle, PMD, SpotBugs) are currently configured.

---

## Step 0 — Add SLF4J Dependency

Before any file can use `LoggerFactory`, the `everything-server/pom.xml` must declare:

```xml
<dependency>
  <groupId>org.slf4j</groupId>
  <artifactId>slf4j-api</artifactId>
  <version>2.0.12</version>
</dependency>
<dependency>
  <groupId>ch.qos.logback</groupId>
  <artifactId>logback-classic</artifactId>
  <version>1.4.14</version>
</dependency>
```

Verify with `./mvnw dependency:resolve -pl everything-server` before proceeding to R1.

---

## Rules

### R1 — Logging Standard

**Requirement:**

1. Every class that needs logging uses:
   ```java
   private static final Logger log = LoggerFactory.getLogger(ClassName.class);
   // imports: org.slf4j.Logger, org.slf4j.LoggerFactory
   ```

2. **Migrate all 14 `java.util.logging.Logger` fields** to SLF4J:
   `RedisManager`, `CacheServiceImpl`, `MySQLDatabaseManager`, `WorkReminderEmailDispatcher`,
   `SkillLocalizationService`, `SkillServiceImpl`, `FeishuNotifyChannel`, `EmailNotifyChannel`,
   `SmtpEmailService`, `OAuthCallbackServer`, `FeishuOAuthService`, `AuthServiceImpl`,
   `AdminServiceImpl`, `KnowledgeBaseServiceImpl`

3. **Replace all `System.out.println` / `System.err.println`** in all `src/main/` files with SLF4J.
   This includes `FeishuNotifierImpl` (`System.err.println` × 6) and `FeedbackServiceImpl`
   (`System.err.println` × 1), which are not in the JUL migration list but still need fixing.

4. Convert all `LOGGER.log(Level.X, "msg" + var, e)` to SLF4J parameterized form:
   `log.level("msg: {}", var, e)`. Replace string concatenation with `{}` placeholders.

5. **`java.util.logging` → SLF4J severity mapping:**

   | `java.util.logging.Level` | SLF4J method |
   |--------------------------|--------------|
   | `SEVERE` | `log.error(...)` |
   | `WARNING` | `log.warn(...)` |
   | `INFO` | `log.info(...)` |
   | `FINE` / `FINER` / `FINEST` | `log.debug(...)` |
   | `CONFIG` | `log.debug(...)` |

6. Severity mapping for new log calls:
   - `log.error()` — unexpected failures; always pass the exception object as last argument
   - `log.warn()` — recoverable problems, degraded mode
   - `log.info()` — business milestones (login, import, scheduler start/stop)
   - `log.debug()` — internal state, trace, diagnostic detail

7. **Runner class policy** — `SkillRepoAuditRunner`, `SkillImportRepairRunner`,
   `SkillStandardizationRunner`, `SkillLocalizationBackfillRunner`, `SkillZipInstallRunner`
   are CLI batch utilities. `System.out.println` calls that print user-facing progress or
   summary lines are **kept as-is**. Only error/debug paths within Runner classes are
   converted to SLF4J.

---

### R2 — Exception Handling Standard

**Requirement:**

1. No empty catch blocks. Every catch block must either:
   - Re-throw (wrapped `RuntimeException` preserving cause), or
   - Log: `log.warn("context", e)` or `log.error("context", e)`

2. **Top-level boundary classes** (may still catch `Exception`/`Throwable` at their outermost entry point):
   - `AppInternalScheduler` — outermost tick/run body
   - `NativeOsSchedulerAdapter` — outermost execute/run method only (not helper methods)
   - `OAuthCallbackServer` — HTTP request handler method
   - Any `@Override run()` / `@Override call()` on a `Runnable`/`Callable` submitted to an executor

   All other locations must catch the most specific available exception subtype.

3. Silent discard is only permitted when all three conditions hold:
   - The catch is inside a top-level boundary method
   - A `log.debug()` or `log.warn()` call explains why the exception is discarded
   - The caller's behavior is not affected by the exception (i.e., it is genuinely optional)

4. Propagating checked exceptions across layers: `throw new RuntimeException("descriptive context", e)`. Never drop the original cause.

---

### R3 — Magic Constant Elimination

**Requirement:**

1. **In-scope literals** — must be extracted to named `private static final` constants:
   - Numeric values representing: timeouts, retry counts, max lengths, TTL durations, pool sizes, backoff multipliers
   - String values representing: status values, OS platform names, config property keys, path segment names used in `Path.of()`

2. **Exempt literals:**
   - Numeric literals inside SQL string literals (e.g., `VARCHAR(36)`, `* 1000` in epoch conversion)
   - `0` and `1` used as array indices or loop counters
   - Constants already declared as named `static final` fields

3. Constant naming: `UPPER_SNAKE_CASE`, placed at the top of the class after the logger field.

4. **Path construction rule** — Every inline path construction using `System.getProperty("user.home")`
   must be replaced by a named `private static final` constant declared at class top.
   - Files with `Path.of(System.getProperty("user.home"), ...)` inline: `WorkTodoMetaStore`,
     `WorkReminderEmailDispatcher`, `WorkFollowupServiceImpl`, `WorkReminderStateReader`,
     `AppInternalScheduler`, `NativeOsSchedulerAdapter`, `HrCaseServiceImpl`, `CliAuditStore`,
     `SkillImportRepairRunner` — replace with a named `private static final Path` constant.
   - `LocalDatabaseManager` uses string concatenation (`System.getProperty("user.home") + "/.yeahmobi-everything/data.db"`)
     already assigned to `DEFAULT_DB_PATH` (a `String`). Convert this to `private static final Path DEFAULT_DB_PATH`
     using `Path.of(System.getProperty("user.home"), ".yeahmobi-everything", "data.db")` and update usages.

**Concrete literals to extract (exhaustive list for named files):**

| File | Literal | Constant name |
|------|---------|---------------|
| `WorkReminderEmailDispatcher` | `30` (max retry delay min) | `MAX_RETRY_DELAY_MINUTES` |
| `WorkReminderEmailDispatcher` | `5` (retry backoff multiplier) | `RETRY_BACKOFF_MULTIPLIER` |
| `WorkReminderEmailDispatcher` | `"sent"` | `STATUS_SENT` |
| `WorkReminderEmailDispatcher` | `"failed"` | `STATUS_FAILED` |
| `WorkReminderEmailDispatcher` | `"pending"` | `STATUS_PENDING` |
| `SmtpEmailService` | `"10000"` for `mail.smtp.timeout` | `SMTP_READ_TIMEOUT_MS` |
| `SmtpEmailService` | `"10000"` for `mail.smtp.connectiontimeout` | `SMTP_CONNECT_TIMEOUT_MS` |
| `NativeOsSchedulerAdapter` | `"macos"` (5 occurrences) | `OS_MACOS` |
| `NativeOsSchedulerAdapter` | `"windows"` (4 occurrences) | `OS_WINDOWS` |
| `NativeOsSchedulerAdapter` | `"linux"` (5 occurrences) | `OS_LINUX` |
| `HrCaseServiceImpl` | Path segments → `CASES_FILE` | already declared, verify constant |
| `ChatServiceImpl` | `12000`, `6` | already declared as `MAX_KNOWLEDGE_CONTEXT_CHARS` / `MAX_KNOWLEDGE_SOURCES` ✓ |

For files not in this table but containing literals that meet the R3.1 criteria, apply extraction as discovered during implementation.

---

## Method Extraction (Best-Effort)

Where a method mixes multiple concerns in a way that obscures intent, extract into private methods with self-documenting names. Applied as judgment, not enforced by line count.

Primary targets:
- `ChatServiceImpl.sendMessage()` → extract `buildLlmRequest()` and `parseAgentScopeResponse()`
- `AdminServiceImpl.createSkillFromTemplate()` → extract `resolveSkillProfile(SkillTemplate)` replacing the 4-branch if-else
- `AnthropicSkillImporter.importFromPathDetailed()` → separate manifest parsing from directory walk

---

## What Is Not Changed

- Test code (`src/test/`)
- FXML, SQL, and resource files
- Chinese text in user-facing messages and existing log messages
- Public method signatures and interface contracts
- Module structure and Maven dependency graph (except Step 0)
- SQL literals inside string constants
- `System.out.println` in Runner files that print intentional user-facing progress

---

## Implementation Order

1. **Step 0** — Add SLF4J + Logback to `everything-server/pom.xml`; verify compile
2. **R1** — Migrate 14 JUL files + replace `System.out`/`System.err` in non-Runner files
3. **R2** — Fix exception handling: primary files first, then full sweep
4. **R3** — Extract magic constants per-file alongside R2
5. **Method extraction** — Three targeted methods above
6. **Verify** — `./mvnw test` must pass with zero new failures

---

## Success Criteria (Verifiable)

| Criterion | How to verify |
|-----------|---------------|
| No `System.out`/`System.err` in non-Runner `src/main/` files | `grep -r "System\.\(out\|err\)\.print" everything-server/src/main/java --include="*.java" \| grep -v "Runner\.java"` returns empty |
| No `java.util.logging` imports in `src/main/` | `grep -r "import java\.util\.logging" everything-server/src/main/java` returns empty |
| No empty catch blocks | `grep -rn "catch.*{" everything-server/src/main/java` then manual check for empty bodies |
| All concrete-table literals extracted | Code review of each listed file confirms constants present |
| All tests pass | `./mvnw test` exits 0 |
