# Backend Code Quality Refactoring — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply three coding standards (logging, exception handling, magic constants) uniformly across `everything-server` to improve readability and maintainability.

**Architecture:** Pure refactoring — no behavior changes. Each task migrates one batch of files, keeps existing tests green, and commits. The 14 files still using `java.util.logging` are migrated to SLF4J; all `System.out/err` in non-Runner files are replaced; silent catch blocks get logging; magic literals become named constants.

**Tech Stack:** Java 17, Maven, SLF4J 2.0.x (already transitive via agentscope), Logback 1.4.14 (to add), JUnit 5 + jqwik + Mockito (existing tests)

**Spec:** `docs/superpowers/specs/2026-03-13-backend-code-quality-design.md`

---

## Chunk 1: Dependency Setup + Auth Package

### Task 1: Add Logback binding to everything-server/pom.xml

**Files:**
- Modify: `everything-server/pom.xml`

> **Context:** `slf4j-api` (2.0.17) is already a transitive dependency via `agentscope`. Only a binding (logback-classic) is missing, which means SLF4J silently discards all log output today.

- [ ] **Step 1: Verify baseline tests pass**

  ```bash
  ./mvnw test -pl everything-server -DskipTests=false 2>&1 | tail -5
  ```
  Expected: `BUILD SUCCESS` (or known failures unrelated to logging)

- [ ] **Step 2: Add logback-classic to everything-server/pom.xml**

  In `everything-server/pom.xml`, add inside `<dependencies>` after the Jakarta Mail block:

  ```xml
  <!-- Logging binding (SLF4J → Logback) -->
  <dependency>
      <groupId>ch.qos.logback</groupId>
      <artifactId>logback-classic</artifactId>
      <version>1.4.14</version>
  </dependency>
  ```

- [ ] **Step 3: Verify compile + resolve**

  ```bash
  ./mvnw dependency:resolve -pl everything-server 2>&1 | grep -i logback
  ```
  Expected: `ch.qos.logback:logback-classic:jar:1.4.14:compile`

- [ ] **Step 4: Run tests to confirm nothing broke**

  ```bash
  ./mvnw test -pl everything-server 2>&1 | tail -5
  ```
  Expected: same result as Step 1

- [ ] **Step 5: Commit**

  ```bash
  git add everything-server/pom.xml
  git commit -m "build(server): add logback-classic binding for SLF4J"
  ```

---

### Task 2: R1 — Migrate auth package to SLF4J (4 files)

**Files:**
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/auth/AuthServiceImpl.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/auth/FeishuOAuthService.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/auth/SmtpEmailService.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/auth/OAuthCallbackServer.java`

> **Pattern to apply in every file:**
> 1. Remove `import java.util.logging.Level;` and `import java.util.logging.Logger;`
> 2. Add `import org.slf4j.Logger;` and `import org.slf4j.LoggerFactory;`
> 3. Replace `private static final Logger LOGGER = Logger.getLogger(X.class.getName());`
>    with `private static final Logger log = LoggerFactory.getLogger(X.class);`
> 4. Replace every `LOGGER.log(Level.INFO, "msg", e)` → `log.info("msg", e)`
>    Replace every `LOGGER.log(Level.WARNING, "msg {0}", new Object[]{v})` → `log.warn("msg {}", v)`
>    Replace every `LOGGER.warning("msg")` → `log.warn("msg")`
>    Replace every `LOGGER.info("msg")` → `log.info("msg")`
>    Replace every `LOGGER.severe("msg")` → `log.error("msg")`
>    For format patterns using `{0}`, `{1}`: replace with `{}`, `{}` (SLF4J uses same `{}` syntax)
>    For string concatenation `"msg: " + var`: change to `"msg: {}", var`
>
> **Level mapping:** SEVERE→error, WARNING→warn, INFO→info, FINE/FINER/FINEST/CONFIG→debug

- [ ] **Step 1: Migrate AuthServiceImpl.java**

  Current logger (line 32):
  ```java
  private static final Logger LOGGER = Logger.getLogger(AuthServiceImpl.class.getName());
  ```
  Replace with:
  ```java
  private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
  ```
  Then replace all call sites:
  - `LOGGER.warning("...")` → `log.warn("...")`
  - `LOGGER.info("...")` → `log.info("...")`
  - `LOGGER.log(Level.INFO, "msg: {0}", new Object[]{v})` → `log.info("msg: {}", v)`
  - `LOGGER.log(Level.WARNING, "msg: {0}", new Object[]{v})` → `log.warn("msg: {}", v)`

  Update imports accordingly.

- [ ] **Step 2: Migrate FeishuOAuthService.java**

  Same pattern. Also add `SmtpEmailService` R3 constants (see Task 10 — do NOT do R3 here yet; keep tasks separate).

- [ ] **Step 3: Migrate SmtpEmailService.java**

  Same pattern.

- [ ] **Step 4: Migrate OAuthCallbackServer.java**

  Same pattern. Note: `OAuthCallbackServer` is a top-level boundary class per R2 — its broad `catch (Exception e)` blocks are acceptable and should not be changed in this task.

- [ ] **Step 5: Verify compile**

  ```bash
  ./mvnw compile -pl everything-server 2>&1 | tail -5
  ```
  Expected: `BUILD SUCCESS`

- [ ] **Step 6: Run auth tests**

  ```bash
  ./mvnw test -pl everything-server -Dtest="com.yeahmobi.everything.auth.*" 2>&1 | tail -10
  ```
  Expected: all pass

- [ ] **Step 7: Confirm no remaining JUL imports in auth package**

  ```bash
  grep -r "java.util.logging" everything-server/src/main/java/com/yeahmobi/everything/auth/
  ```
  Expected: empty

- [ ] **Step 8: Commit**

  ```bash
  git add everything-server/src/main/java/com/yeahmobi/everything/auth/
  git commit -m "refactor(auth): migrate java.util.logging to SLF4J"
  ```

---

### Task 3: R1 — Migrate repository/cache package (3 files)

**Files:**
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/repository/cache/RedisManager.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/repository/cache/CacheServiceImpl.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/repository/mysql/MySQLDatabaseManager.java`

> Apply the same JUL→SLF4J pattern from Task 2.
> For `CacheServiceImpl`: its catch blocks use `log.warn`/`log.error` with broad `Exception` — those are acceptable because Redis is an optional external service. Do not change exception type in this task (R2 sweep will handle it).

- [ ] **Step 1: Migrate RedisManager.java** — apply JUL→SLF4J pattern

- [ ] **Step 2: Migrate CacheServiceImpl.java** — apply JUL→SLF4J pattern

- [ ] **Step 3: Migrate MySQLDatabaseManager.java** — apply JUL→SLF4J pattern

- [ ] **Step 4: Verify compile + tests**

  ```bash
  ./mvnw test -pl everything-server -Dtest="com.yeahmobi.everything.repository.*" 2>&1 | tail -10
  ```
  Expected: all pass (MySQL/Redis tests may be skipped if DB not available — that is fine)

- [ ] **Step 5: Confirm no remaining JUL in cache/mysql packages**

  ```bash
  grep -r "java.util.logging" everything-server/src/main/java/com/yeahmobi/everything/repository/
  ```
  Expected: empty

- [ ] **Step 6: Commit**

  ```bash
  git add everything-server/src/main/java/com/yeahmobi/everything/repository/
  git commit -m "refactor(repository): migrate java.util.logging to SLF4J"
  ```

---

## Chunk 2: Remaining JUL Migrations

### Task 4: R1 — Migrate skill package (SkillLocalizationService, SkillServiceImpl)

**Files:**
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/skill/SkillLocalizationService.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/skill/SkillServiceImpl.java`

- [ ] **Step 1: Migrate SkillLocalizationService.java** — JUL→SLF4J pattern

- [ ] **Step 2: Migrate SkillServiceImpl.java** — JUL→SLF4J pattern

- [ ] **Step 3: Verify compile + skill tests**

  ```bash
  ./mvnw test -pl everything-server -Dtest="com.yeahmobi.everything.skill.*" 2>&1 | tail -10
  ```

- [ ] **Step 4: Commit**

  ```bash
  git add everything-server/src/main/java/com/yeahmobi/everything/skill/SkillLocalizationService.java \
          everything-server/src/main/java/com/yeahmobi/everything/skill/SkillServiceImpl.java
  git commit -m "refactor(skill): migrate java.util.logging to SLF4J"
  ```

---

### Task 5: R1 — Migrate opsos notify channels (FeishuNotifyChannel, EmailNotifyChannel)

**Files:**
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/opsos/notify/FeishuNotifyChannel.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/opsos/notify/EmailNotifyChannel.java`

- [ ] **Step 1: Migrate FeishuNotifyChannel.java** — JUL→SLF4J pattern

- [ ] **Step 2: Migrate EmailNotifyChannel.java** — JUL→SLF4J pattern

- [ ] **Step 3: Verify compile**

  ```bash
  ./mvnw compile -pl everything-server 2>&1 | tail -5
  ```

- [ ] **Step 4: Commit**

  ```bash
  git add everything-server/src/main/java/com/yeahmobi/everything/opsos/
  git commit -m "refactor(opsos): migrate java.util.logging to SLF4J"
  ```

---

### Task 6: R1 — Migrate admin + knowledge (AdminServiceImpl, KnowledgeBaseServiceImpl)

**Files:**
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/admin/AdminServiceImpl.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/knowledge/KnowledgeBaseServiceImpl.java`

- [ ] **Step 1: Migrate AdminServiceImpl.java** — JUL→SLF4J pattern

- [ ] **Step 2: Migrate KnowledgeBaseServiceImpl.java** — JUL→SLF4J pattern

- [ ] **Step 3: Verify compile + tests**

  ```bash
  ./mvnw test -pl everything-server -Dtest="com.yeahmobi.everything.admin.*,com.yeahmobi.everything.knowledge.*" 2>&1 | tail -10
  ```

- [ ] **Step 4: Commit**

  ```bash
  git add everything-server/src/main/java/com/yeahmobi/everything/admin/AdminServiceImpl.java \
          everything-server/src/main/java/com/yeahmobi/everything/knowledge/KnowledgeBaseServiceImpl.java
  git commit -m "refactor(admin,knowledge): migrate java.util.logging to SLF4J"
  ```

---

### Task 7: R1 — Migrate WorkReminderEmailDispatcher

**Files:**
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/workfollowup/WorkReminderEmailDispatcher.java`

> This file has the most log call sites (~20). Also contains magic constants (Task 11 handles those) and retry logic (Task 9 handles R2). In this task only do the JUL→SLF4J migration.

- [ ] **Step 1: Replace logger declaration**

  Remove:
  ```java
  import java.util.logging.Level;
  import java.util.logging.Logger;
  private static final Logger LOGGER = Logger.getLogger(WorkReminderEmailDispatcher.class.getName());
  ```
  Add:
  ```java
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  private static final Logger log = LoggerFactory.getLogger(WorkReminderEmailDispatcher.class);
  ```

- [ ] **Step 2: Replace all LOGGER call sites** — apply level mapping from spec. Special attention:
  - `LOGGER.log(Level.FINE, ...)` → `log.debug(...)`
  - Replace `new Object[]{v1, v2}` array format args with `v1, v2` positional args
  - Replace string concatenation in message with `{}` placeholders

- [ ] **Step 3: Verify compile**

  ```bash
  ./mvnw compile -pl everything-server 2>&1 | tail -5
  ```

- [ ] **Step 4: Commit**

  ```bash
  git add everything-server/src/main/java/com/yeahmobi/everything/workfollowup/WorkReminderEmailDispatcher.java
  git commit -m "refactor(workfollowup): migrate java.util.logging to SLF4J"
  ```

---

### Task 8: R1 — Replace System.out/err in non-Runner files (3 files)

**Files:**
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/chat/ChatServiceImpl.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/notification/FeishuNotifierImpl.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/feedback/FeedbackServiceImpl.java`

> **ChatServiceImpl** has no Logger yet. Add one. The 4 `System.out.println` are debug-level traces.
> **FeishuNotifierImpl** has no Logger. Add one. The 6 `System.err.println` are warn/error-level.
> **FeedbackServiceImpl** has no Logger. Add one. The 1 `System.err.println` is warn-level.

- [ ] **Step 1: Add Logger to ChatServiceImpl and replace System.out calls**

  Add after the class constant declarations:
  ```java
  private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);
  ```
  Add imports:
  ```java
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  ```
  Replace (lines ~134-135, ~693-694):
  ```java
  // Before:
  System.out.println("[ChatService] Sending request to AgentScope: " + apiUrl);
  System.out.println("[ChatService] Timeout: " + getTimeoutSeconds() + "s");
  System.out.println("[ChatService] Config agentscope.server.url=" + baseUrl);
  System.out.println("[ChatService] Config agentscope.server.port=" + config.getAgentScopeServerPort());

  // After:
  log.debug("Sending request to AgentScope: {}", apiUrl);
  log.debug("Timeout: {}s", getTimeoutSeconds());
  log.debug("Config agentscope.server.url={}", baseUrl);
  log.debug("Config agentscope.server.port={}", config.getAgentScopeServerPort());
  ```

- [ ] **Step 2: Add Logger to FeishuNotifierImpl and replace System.err calls**

  Add after existing constants:
  ```java
  private static final Logger log = LoggerFactory.getLogger(FeishuNotifierImpl.class);
  ```
  Replace all `System.err.println(...)` with `log.warn(...)` or `log.error(...)` as appropriate:
  - Configuration-missing messages (admin user not configured, app_id not configured) → `log.warn("...")`
  - Exception messages (Failed to send...) → `log.error("...: {}", e.getMessage())` or pass exception: `log.error("Failed to send Feishu notification", e)`

- [ ] **Step 3: Add Logger to FeedbackServiceImpl and replace System.err call**

  Add Logger field. Replace:
  ```java
  System.err.println("Failed to send Feishu notification: " + e.getMessage());
  ```
  With:
  ```java
  log.warn("Failed to send Feishu notification", e);
  ```

- [ ] **Step 4: Verify no remaining System.out/err in non-Runner src/main/**

  ```bash
  grep -r "System\.\(out\|err\)\.print" everything-server/src/main/java --include="*.java" | grep -v "Runner\.java"
  ```
  Expected: empty output

- [ ] **Step 5: Verify compile + tests**

  ```bash
  ./mvnw test -pl everything-server -Dtest="com.yeahmobi.everything.chat.*,com.yeahmobi.everything.feedback.*" 2>&1 | tail -10
  ```

- [ ] **Step 6: Commit**

  ```bash
  git add everything-server/src/main/java/com/yeahmobi/everything/chat/ChatServiceImpl.java \
          everything-server/src/main/java/com/yeahmobi/everything/notification/FeishuNotifierImpl.java \
          everything-server/src/main/java/com/yeahmobi/everything/feedback/FeedbackServiceImpl.java
  git commit -m "refactor(chat,notification,feedback): replace System.out/err with SLF4J"
  ```

---

### Task 9: Checkpoint — Verify full R1 completion

- [ ] **Step 1: Check no java.util.logging remains in src/main/**

  ```bash
  grep -r "import java\.util\.logging" everything-server/src/main/java --include="*.java"
  ```
  Expected: empty

- [ ] **Step 2: Check no System.out/err in non-Runner src/main/**

  ```bash
  grep -r "System\.\(out\|err\)\.print" everything-server/src/main/java --include="*.java" | grep -v "Runner\.java"
  ```
  Expected: empty

- [ ] **Step 3: Run full test suite**

  ```bash
  ./mvnw test -pl everything-server 2>&1 | tail -15
  ```
  Expected: `BUILD SUCCESS`, zero new failures

---

## Chunk 3: R2 Exception Handling

### Task 10: R2 — Fix silently swallowed exceptions in primary files

**Files:**
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/skill/AnthropicSkillImporter.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/machineops/NativeOsSchedulerAdapter.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/common/Config.java`

> **Rule:** Every catch block must either re-throw or log. Silent swallow (`catch (Exception ex) { failed++; }`) is only allowed if accompanied by a `log.warn/debug` explaining why.

- [ ] **Step 1: Fix AnthropicSkillImporter.java**

  Locate the two swallowed-exception catch blocks. Add a Logger if not present. Add logging:
  ```java
  // Pattern to apply:
  } catch (Exception ex) {
      failed++;
      log.warn("Failed to import skill from path, skipping: {}", ex.getMessage());
  }
  ```
  For the localization persistence failure:
  ```java
  } catch (Exception ignored) {
      localizationFailed++;
      log.debug("Localization persistence failed for skill, continuing: {}", ignored.getMessage());
  }
  ```

- [ ] **Step 2: Fix NativeOsSchedulerAdapter.java**

  Locate `Files.deleteIfExists` catch block. Replace empty catch:
  ```java
  // Before:
  } catch (Exception ignored) {
      // ignore delete errors
  }
  // After:
  } catch (IOException e) {
      log.warn("Failed to delete temp file, continuing: {}", e.getMessage());
  }
  ```
  Add Logger if not present. Change `catch (Exception ignored)` to `catch (IOException e)` (specific type for file IO).

- [ ] **Step 3: Fix Config.java**

  Locate `loadProperties()` method. Find the `IOException` catch. Add logging:
  ```java
  } catch (IOException e) {
      log.warn("Failed to load application.properties, using defaults: {}", e.getMessage());
  }
  ```
  Add Logger field and SLF4J imports if not present.

- [ ] **Step 4: Verify compile + tests**

  ```bash
  ./mvnw test -pl everything-server -Dtest="com.yeahmobi.everything.skill.*,com.yeahmobi.everything.common.*" 2>&1 | tail -10
  ```

- [ ] **Step 5: Commit**

  ```bash
  git add everything-server/src/main/java/com/yeahmobi/everything/skill/AnthropicSkillImporter.java \
          everything-server/src/main/java/com/yeahmobi/everything/machineops/NativeOsSchedulerAdapter.java \
          everything-server/src/main/java/com/yeahmobi/everything/common/Config.java
  git commit -m "fix(server): log swallowed exceptions in AnthropicSkillImporter, NativeOsSchedulerAdapter, Config"
  ```

---

### Task 11: R2 — Exception handling sweep (remaining files)

**Files:** All `src/main/java` files not already addressed in Task 10.

> **What to look for:**
> - Empty catch blocks: `} catch (Exception e) { }` (body with only whitespace or a comment)
> - Catch blocks that only increment a counter but never log
> - Broad `catch (Exception e)` outside of the 4 top-level boundary classes (`AppInternalScheduler`, `NativeOsSchedulerAdapter` top-level only, `OAuthCallbackServer`, Runnable.run implementations)
>
> **Top-level boundaries (may keep broad catch):**
> `AppInternalScheduler`, `OAuthCallbackServer`, any `run()`/`call()` submitted to executor

- [ ] **Step 1: Find all empty catch blocks**

  ```bash
  grep -rn "catch" everything-server/src/main/java --include="*.java" -A 3 | grep -B2 "^\s*--\s*$\|^.*catch.*}\s*$"
  ```
  Also manually check: `WorkReminderEmailDispatcher`, `CacheServiceImpl`, all repository implementations.

- [ ] **Step 2: Fix each empty/silent catch block**

  For each catch block with no log call:
  - If the exception is genuinely ignorable in a boundary method: add `log.debug("skipping ..., reason: {}", e.getMessage())`
  - If the exception should bubble up: add `throw new RuntimeException("context description", e)`
  - If the exception indicates recoverable degraded state: add `log.warn("... degraded mode", e)`

  `CacheServiceImpl` broad catches are acceptable (Redis is optional service) — ensure each has a `log.warn("Redis operation failed, degraded mode", e)` call.

- [ ] **Step 3: Verify compile + full tests**

  ```bash
  ./mvnw test -pl everything-server 2>&1 | tail -15
  ```
  Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

  ```bash
  # Stage only files that were actually modified in this sweep (check git status first)
  git status everything-server/src/main/java/
  git add -p everything-server/src/main/java/  # interactive staging, or list specific files
  git commit -m "fix(server): eliminate silent exception swallowing across server module"
  ```

---

## Chunk 4: R3 Magic Constants + Method Extraction

### Task 12: R3 — Magic constants in WorkReminderEmailDispatcher + SmtpEmailService

**Files:**
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/workfollowup/WorkReminderEmailDispatcher.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/auth/SmtpEmailService.java`

- [ ] **Step 1: Add constants to WorkReminderEmailDispatcher**

  Add after the logger field:
  ```java
  private static final int MAX_RETRY_DELAY_MINUTES = 30;
  private static final int RETRY_BACKOFF_MULTIPLIER = 5;
  private static final String STATUS_SENT = "sent";
  private static final String STATUS_FAILED = "failed";
  private static final String STATUS_PENDING = "pending";
  ```
  Replace all usages:
  - `Math.min(30, 5 * attempts)` → `Math.min(MAX_RETRY_DELAY_MINUTES, RETRY_BACKOFF_MULTIPLIER * attempts)`
  - `"sent".equalsIgnoreCase(old.status())` → `STATUS_SENT.equalsIgnoreCase(old.status())`
  - `"sent"` in assignments → `STATUS_SENT`
  - `"failed"` in assignments → `STATUS_FAILED`
  - `"pending"` in assignments → `STATUS_PENDING`

- [ ] **Step 2: Add constants to SmtpEmailService**

  Add after the logger field:
  ```java
  private static final String SMTP_READ_TIMEOUT_MS = "10000";
  private static final String SMTP_CONNECT_TIMEOUT_MS = "10000";
  ```
  Replace:
  ```java
  // Before:
  props.put("mail.smtp.timeout", "10000");
  props.put("mail.smtp.connectiontimeout", "10000");
  // After:
  props.put("mail.smtp.timeout", SMTP_READ_TIMEOUT_MS);
  props.put("mail.smtp.connectiontimeout", SMTP_CONNECT_TIMEOUT_MS);
  ```

- [ ] **Step 3: Verify compile + tests**

  ```bash
  ./mvnw test -pl everything-server -Dtest="com.yeahmobi.everything.auth.*" 2>&1 | tail -10
  ```

- [ ] **Step 4: Commit**

  ```bash
  git add everything-server/src/main/java/com/yeahmobi/everything/workfollowup/WorkReminderEmailDispatcher.java \
          everything-server/src/main/java/com/yeahmobi/everything/auth/SmtpEmailService.java
  git commit -m "refactor(server): extract magic constants in WorkReminderEmailDispatcher and SmtpEmailService"
  ```

---

### Task 13: R3 — OS platform strings in NativeOsSchedulerAdapter

**Files:**
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/machineops/NativeOsSchedulerAdapter.java`

- [ ] **Step 1: Add constants**

  Add after logger field:
  ```java
  private static final String OS_MACOS = "macos";
  private static final String OS_WINDOWS = "windows";
  private static final String OS_LINUX = "linux";
  ```

- [ ] **Step 2: Replace all 14 occurrences (5 × "macos" + 4 × "windows" + 5 × "linux")**

  Replace every `"macos"`, `"windows"`, `"linux"` string literal with the corresponding constant.
  ```java
  // Before: if ("macos".equals(os)) {
  // After:  if (OS_MACOS.equals(os)) {
  ```
  Verify counts after replacement:
  ```bash
  grep -c '"macos"\|"windows"\|"linux"' everything-server/src/main/java/com/yeahmobi/everything/machineops/NativeOsSchedulerAdapter.java
  ```
  Expected: `0`

- [ ] **Step 3: Verify compile**

  ```bash
  ./mvnw compile -pl everything-server 2>&1 | tail -5
  ```

- [ ] **Step 4: Commit**

  ```bash
  git add everything-server/src/main/java/com/yeahmobi/everything/machineops/NativeOsSchedulerAdapter.java
  git commit -m "refactor(machineops): extract OS platform name constants"
  ```

---

### Task 14: R3 — Path constants sweep (10 files)

**Files:**
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/workfollowup/WorkTodoMetaStore.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/workfollowup/WorkReminderEmailDispatcher.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/workfollowup/WorkFollowupServiceImpl.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/workfollowup/WorkReminderStateReader.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/machineops/AppInternalScheduler.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/machineops/NativeOsSchedulerAdapter.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/hrassist/HrCaseServiceImpl.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/machineops/CliAuditStore.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/skill/SkillImportRepairRunner.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/repository/local/LocalDatabaseManager.java`

> **Pattern:** Replace inline `Path.of(System.getProperty("user.home"), ...)` with a named
> `private static final Path` constant declared at class top.
>
> For `LocalDatabaseManager`: convert the existing `String` constant `DEFAULT_DB_PATH` to
> `private static final Path DEFAULT_DB_PATH = Path.of(System.getProperty("user.home"), ".yeahmobi-everything", "data.db");`
> and update all usages from string `.toString()` style to `Path` usage directly.

- [ ] **Step 1: For each of the 9 Path.of files**, find inline path construction and extract:

  Example pattern (adapt to actual path segments in each file):
  ```java
  // Before (inline in field or method):
  Path stateFile = Path.of(System.getProperty("user.home"), ".everything-assistant", "work-followup-notify-state.tsv");

  // After:
  private static final Path STATE_FILE =
      Path.of(System.getProperty("user.home"), ".everything-assistant", "work-followup-notify-state.tsv");
  // ... then use STATE_FILE in method bodies
  ```

  Name the constant to reflect the file's purpose: `STATE_FILE`, `CASES_FILE`, `AUDIT_FILE`, `SCHEDULER_FILE`, `META_STORE_DIR`, etc.

- [ ] **Step 2: Fix LocalDatabaseManager**

  Locate `DEFAULT_DB_PATH` (currently a `String`). Replace:
  ```java
  // Before:
  private static final String DEFAULT_DB_PATH =
      System.getProperty("user.home") + "/.yeahmobi-everything/data.db";

  // After:
  private static final Path DEFAULT_DB_PATH =
      Path.of(System.getProperty("user.home"), ".yeahmobi-everything", "data.db");
  ```
  Update all usages to use `DEFAULT_DB_PATH.toString()` where a `String` is needed, or `DEFAULT_DB_PATH` directly where a `Path` is needed. Add `import java.nio.file.Path;` if not already present.

- [ ] **Step 3: Verify compile**

  ```bash
  ./mvnw compile -pl everything-server 2>&1 | tail -5
  ```

- [ ] **Step 4: Run repository tests**

  ```bash
  ./mvnw test -pl everything-server -Dtest="com.yeahmobi.everything.repository.*" 2>&1 | tail -10
  ```

- [ ] **Step 5: Commit**

  ```bash
  git add everything-server/src/main/java/
  git commit -m "refactor(server): replace inline Path.of(user.home) with named constants"
  ```

---

### Task 15: Method extraction — ChatServiceImpl, AdminServiceImpl, AnthropicSkillImporter

**Files:**
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/chat/ChatServiceImpl.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/admin/AdminServiceImpl.java`
- Modify: `everything-server/src/main/java/com/yeahmobi/everything/skill/AnthropicSkillImporter.java`

> These are best-effort extractions — no line count requirement. Goal: a method name should explain what it does without reading the body.

- [ ] **Step 1: ChatServiceImpl — extract buildLlmRequest()**

  In `sendMessage()`, identify the block that constructs the JSON request body (Gson JsonObject building for LLM/AgentScope API call). Extract it to:
  ```java
  private JsonObject buildLlmRequest(String skillId, String userMessage, List<ChatMessage> history, String knowledgeContext) {
      // ... moved JSON construction code
  }
  ```
  Call site: `JsonObject request = buildLlmRequest(skillId, userMessage, history, knowledgeContext);`

- [ ] **Step 2: ChatServiceImpl — extract parseAgentScopeResponse()**

  Identify the block that parses the HTTP response string to extract the assistant reply. Extract to:
  ```java
  private String parseAgentScopeResponse(String responseBody) {
      // ... moved parsing code
  }
  ```

- [ ] **Step 3: AdminServiceImpl — extract resolveSkillProfile(SkillTemplate)**

  Locate the 4-branch if-else in `createSkillFromTemplate()` that sets `toolIds`, `toolGroups`, `contextPolicy`, and augments `promptTemplate` based on skill type.

  Declare a local record inside `AdminServiceImpl` (Java 16+ local records are supported in Java 17):
  ```java
  private record SkillProfile(
      List<String> toolIds,
      List<String> toolGroups,
      String contextPolicy,
      String finalPrompt
  ) {}
  ```
  Then extract:
  ```java
  private SkillProfile resolveSkillProfile(SkillTemplate template, String basePrompt) {
      if (isInformationRetrievalSkill(template.name(), template.description(), template.category())) {
          return new SkillProfile(
              List.of("web-research"),
              List.of("web-search", "information-retrieval"),
              "standard",
              strengthenInformationRetrievalPrompt(basePrompt, template.name())
          );
      } else if (isWorkFollowupSkill(template.name(), template.description(), template.category())) {
          // ... etc
      }
      // default fallback
      return new SkillProfile(List.of(), List.of(), "standard", basePrompt);
  }
  ```
  Call site in `createSkillFromTemplate()`: `SkillProfile profile = resolveSkillProfile(template, promptTemplate);`
  Then use `profile.toolIds()`, `profile.toolGroups()`, etc.

  The goal is that `createSkillFromTemplate()` reads: "determine profile, then build skill".

- [ ] **Step 4: AnthropicSkillImporter — separate manifest parsing from directory walk**

  In `importFromPathDetailed()`, identify:
  - The directory walk / file discovery block
  - The manifest parsing block

  Extract the manifest parsing for a single file to:
  ```java
  private SkillImportResult parseAndImportManifest(Path manifestFile) {
      // ... reads and parses single SKILL.md or manifest.json
  }
  ```
  The outer method then becomes: walk directory → for each manifest file → call parseAndImportManifest.

- [ ] **Step 5: Verify compile + tests**

  ```bash
  ./mvnw test -pl everything-server 2>&1 | tail -15
  ```
  Expected: BUILD SUCCESS, all existing tests pass

- [ ] **Step 6: Commit**

  ```bash
  git add everything-server/src/main/java/com/yeahmobi/everything/chat/ChatServiceImpl.java \
          everything-server/src/main/java/com/yeahmobi/everything/admin/AdminServiceImpl.java \
          everything-server/src/main/java/com/yeahmobi/everything/skill/AnthropicSkillImporter.java
  git commit -m "refactor(server): extract long methods in ChatServiceImpl, AdminServiceImpl, AnthropicSkillImporter"
  ```

---

### Task 16: Final verification

- [ ] **Step 1: No java.util.logging in src/main/**

  ```bash
  grep -r "import java\.util\.logging" everything-server/src/main/java --include="*.java"
  ```
  Expected: empty

- [ ] **Step 2: No System.out/err in non-Runner src/main/**

  ```bash
  grep -r "System\.\(out\|err\)\.print" everything-server/src/main/java --include="*.java" | grep -v "Runner\.java"
  ```
  Expected: empty

- [ ] **Step 3: No empty catch blocks**

  ```bash
  grep -rn "catch" everything-server/src/main/java --include="*.java" -A 1 | grep -B1 "^--$\|^\s*}$"
  ```
  Review any results manually to confirm all have a log call or re-throw.

- [ ] **Step 4: Spot-check concrete constants table**

  ```bash
  grep -n "MAX_RETRY_DELAY_MINUTES\|RETRY_BACKOFF_MULTIPLIER\|STATUS_SENT\|STATUS_FAILED\|STATUS_PENDING" \
      everything-server/src/main/java/com/yeahmobi/everything/workfollowup/WorkReminderEmailDispatcher.java
  grep -n "SMTP_READ_TIMEOUT_MS\|SMTP_CONNECT_TIMEOUT_MS" \
      everything-server/src/main/java/com/yeahmobi/everything/auth/SmtpEmailService.java
  grep -n "OS_MACOS\|OS_WINDOWS\|OS_LINUX" \
      everything-server/src/main/java/com/yeahmobi/everything/machineops/NativeOsSchedulerAdapter.java
  ```
  Each grep should return at least one constant declaration line.

- [ ] **Step 5: Run full test suite**

  ```bash
  ./mvnw test -pl everything-server 2>&1 | tail -20
  ```
  Expected: `BUILD SUCCESS`

- [ ] **Step 6: Final commit (if any unstaged changes remain)**

  ```bash
  git status  # review untracked/modified files before staging
  # Stage only everything-server/src/main/java/ — do NOT use git add -A
  git diff --name-only everything-server/src/main/java/ | xargs git add
  git commit -m "refactor(server): complete code quality refactoring — R1/R2/R3 applied"
  ```
