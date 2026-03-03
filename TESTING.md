# 本地测试指南

> Yeahmobi Everything 本地测试环境搭建、执行与最佳实践

---

## 目录

- [测试体系概览](#测试体系概览)
- [前置准备](#前置准备)
- [快速开始](#快速开始)
- [测试框架说明](#测试框架说明)
- [按模块运行测试](#按模块运行测试)
- [测试分类与命名规范](#测试分类与命名规范)
- [InMemory 测试辅助类](#inmemory-测试辅助类)
- [数据库相关测试](#数据库相关测试)
- [常见问题排查](#常见问题排查)

---

## 测试体系概览

本项目采用 **三层测试策略**：

| 层级 | 框架 | 用途 | 命名规则 |
|------|------|------|----------|
| 单元测试 | JUnit 5 + Mockito | 验证单个类/方法的行为 | `*Test.java` |
| 属性测试 | jqwik | 基于随机输入验证业务不变量 | `*PropertyTest.java` |
| 集成测试 | JUnit 5 | 验证数据库、缓存等真实组件交互 | `*Test.java` |

**测试统计**：

- 测试文件总数：60+
- Property-based 测试：35 个
- InMemory 辅助类：3 个
- 覆盖模块：auth / chat / skill / admin / knowledge / feedback / notification / repository / ui

---

## 前置准备

### 1. JDK 17+

```bash
# Windows (Scoop)
scoop install openjdk17

# Windows (Chocolatey)
choco install temurin17

# 手动安装
# 下载地址：https://adoptium.net/
```

验证安装：

```bash
java -version
# 输出应包含 "17" 或更高版本
```

### 2. Maven（已内置 Maven Wrapper）

项目根目录已包含 `mvnw` / `mvnw.cmd`，**无需额外安装 Maven**。

Windows 上使用 `mvnw.cmd`，macOS / Linux 上使用 `./mvnw`。

### 3. MySQL 8.x（集成测试需要）

> **注意**：纯单元测试和 Property-based 测试**不需要** MySQL。它们使用 InMemory 实现或 Mockito 模拟。
> 仅在运行 Repository 集成测试（如 `MySQLDatabaseManagerTest`、`UserRepositoryImplTest`）时需要。

```bash
# Windows - 下载安装：
# https://dev.mysql.com/downloads/installer/

# 创建测试数据库
mysql -u root -p
```

```sql
CREATE DATABASE yeahmobi_everything CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SOURCE src/main/resources/sql/init-mysql.sql;
```

### 4. Redis（可选）

> 仅在运行 `CacheServiceImplTest` 等缓存集成测试时需要。  
> 大部分测试使用 `NoOpCacheService` 降级实现，**不依赖 Redis**。

```bash
# Windows (WSL2 推荐)
sudo apt install redis-server
sudo service redis-server start

# 或下载 Windows 版本：
# https://github.com/tporadowski/redis/releases

# 验证
redis-cli ping
# 应返回 PONG
```

### 前置依赖汇总

| 依赖 | 用途 | 运行纯单元测试 | 运行集成测试 |
|------|------|:-:|:-:|
| JDK 17+ | 编译运行 | **必需** | **必需** |
| Maven | 构建 | 已内置（Wrapper） | 已内置（Wrapper） |
| MySQL 8.x | Repository 层集成测试 | 不需要 | **必需** |
| Redis 6.x+ | 缓存层集成测试 | 不需要 | 可选 |

---

## 快速开始

### 运行全部测试

```bash
# Windows
mvnw.cmd clean test

# macOS / Linux
./mvnw clean test
```

### 跳过测试直接构建

```bash
mvnw.cmd clean package -DskipTests
```

### 运行单个测试类

```bash
mvnw.cmd test -Dtest=AuthServiceTest
```

### 运行单个测试方法

```bash
mvnw.cmd test -Dtest=AuthServiceTest#testLoginWithValidCredentials
```

### 运行指定模块的测试

```bash
# 运行所有 auth 模块测试
mvnw.cmd test -Dtest="com.yeahmobi.everything.auth.*"

# 运行所有 knowledge 模块测试
mvnw.cmd test -Dtest="com.yeahmobi.everything.knowledge.*"
```

### 仅运行 Property-based 测试

```bash
mvnw.cmd test -Dtest="**/*PropertyTest"
```

### 仅运行传统单元测试（排除 Property 测试）

```bash
mvnw.cmd test -Dtest="**/*Test" -Dexclude="**/*PropertyTest"
```

---

## 测试框架说明

### JUnit 5（核心框架）

项目使用 JUnit Jupiter 5.10.2 作为测试基础框架。

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    @BeforeEach
    void setUp() { /* 初始化 */ }

    @Test
    void testLoginWithValidCredentials() {
        // 验证逻辑
    }
}
```

### Mockito（Mock 框架）

版本：5.10.0。用于模拟外部依赖（数据库、API、缓存）。

```java
import static org.mockito.Mockito.*;

// 模拟 Repository
UserRepository mockRepo = mock(UserRepository.class);
when(mockRepo.findByEmail("test@yeahmobi.com")).thenReturn(testUser);
```

**典型使用场景**：
- `ChatServiceImplTest` — Mock LLM API 调用
- `SkillServiceImplTest` — Mock SkillRepository
- `FeishuOAuthServiceTest` — Mock HTTP 请求
- `KnowledgeControllerTest` — Mock UI 层依赖

### jqwik（Property-based 测试）

版本：1.8.3。通过随机生成输入数据验证业务不变量，能发现传统用例难以覆盖的边界情况。

```java
import net.jqwik.api.*;

class SkillSearchFilterPropertyTest {
    @Property
    void searchResultsShouldAlwaysMatchKeyword(@ForAll @StringLength(min = 1, max = 50) String keyword) {
        // 验证：搜索结果中每一项都包含关键字
    }
}
```

**jqwik 测试特点**：
- 使用 `@Property` 替代 `@Test`
- 使用 `@ForAll` 自动生成随机输入
- 每个属性默认运行 1000 次（可配置）
- 失败时自动 shrink 到最小反例

---

## 按模块运行测试

### auth 模块（认证）

```bash
mvnw.cmd test -Dtest="com.yeahmobi.everything.auth.*"
```

| 测试类 | 类型 | 说明 |
|--------|------|------|
| `AuthServiceTest` | Unit + Mock | 登录、注册核心逻辑 |
| `FeishuOAuthServiceTest` | Unit + Mock | 飞书 OAuth 流程 |
| `AuthServicePropertyTest` | Property | 认证不变量验证 |
| `AuthServiceRegisterLoginPropertyTest` | Property | 注册-登录往返一致性 |
| `AuthServiceLogoutPropertyTest` | Property | 登出会话清理 |
| `AuthServiceInvalidCodePropertyTest` | Property | 无效授权码处理 |

### chat 模块（对话）

```bash
mvnw.cmd test -Dtest="com.yeahmobi.everything.chat.*"
```

| 测试类 | 类型 | 说明 |
|--------|------|------|
| `ChatServiceImplTest` | Unit + Mock | 消息发送、上下文组装 |
| `ChatServiceLlmRequestPropertyTest` | Property | LLM 请求参数正确性 |

### skill 模块（技能）

```bash
mvnw.cmd test -Dtest="com.yeahmobi.everything.skill.*"
```

| 测试类 | 类型 | 说明 |
|--------|------|------|
| `SkillServiceImplTest` | Unit + Mock | Skill CRUD 逻辑 |
| `SkillSearchFilterPropertyTest` | Property | 搜索过滤正确性 |
| `SkillCategoryFilterPropertyTest` | Property | 分类筛选不变量 |
| `SkillTypeFilterPropertyTest` | Property | 类型筛选不变量 |

### knowledge 模块（知识库）

```bash
mvnw.cmd test -Dtest="com.yeahmobi.everything.knowledge.*"
```

| 测试类 | 类型 | 说明 |
|--------|------|------|
| `KnowledgeBindingRoundTripPropertyTest` | Property | 绑定往返一致性 |
| `KnowledgeDeleteCascadePropertyTest` | Property | 级联删除正确性 |
| `KnowledgeContextBuildPropertyTest` | Property | 知识上下文组装 |
| `KnowledgeFormatValidationPropertyTest` | Property | 文件格式校验 |
| `BatchUploadConsistencyPropertyTest` | Property | 批量上传一致性 |
| `TextExtractionRoundTripPropertyTest` | Property | 文本提取往返 |
| `ManualInputRoundTripPropertyTest` | Property | 手动输入往返 |
| `KnowledgeUnbindPropertyTest` | Property | 解绑逻辑 |
| `KnowledgeUpdatePreservesBindingPropertyTest` | Property | 更新保留绑定 |

### admin 模块（管理端）

```bash
mvnw.cmd test -Dtest="com.yeahmobi.everything.admin.*"
```

| 测试类 | 类型 | 说明 |
|--------|------|------|
| `SkillTemplateCreateRoundTripPropertyTest` | Property | Skill 模板创建往返 |
| `SkillTemplateValidationPropertyTest` | Property | 模板校验 |
| `SkillCommandParserPropertyTest` | Property | 命令解析 |
| `SkillCacheInvalidationPropertyTest` | Property | 缓存失效逻辑 |
| `PromptTemplateRenderPropertyTest` | Property | Prompt 模板渲染 |
| `AdminServiceTogglePropertyTest` | Property | 启用/禁用切换 |
| `FeedbackListOrderPropertyTest` | Property | 反馈列表排序 |
| `FeedbackStatusUpdatePropertyTest` | Property | 反馈状态更新 |
| `KnowledgeSkillAutoBindPropertyTest` | Property | 知识自动绑定 |

### repository 模块（数据访问层）

```bash
# 本地 SQLite
mvnw.cmd test -Dtest="com.yeahmobi.everything.repository.local.*"

# MySQL
mvnw.cmd test -Dtest="com.yeahmobi.everything.repository.mysql.*"

# Redis 缓存
mvnw.cmd test -Dtest="com.yeahmobi.everything.repository.cache.*"
```

---

## 测试分类与命名规范

项目遵循以下命名约定，Maven Surefire 插件据此自动识别测试类：

| 后缀 | 类型 | Surefire 匹配规则 | 示例 |
|------|------|-------------------|------|
| `*Test.java` | 单元/集成测试 | `**/*Test.java` | `AuthServiceTest.java` |
| `*Tests.java` | 测试套件 | `**/*Tests.java` | — |
| `*PropertyTest.java` | Property-based | `**/*Test.java` | `SkillSearchFilterPropertyTest.java` |

---

## InMemory 测试辅助类

项目提供了 3 个 InMemory 实现，用于在测试中替代真实数据库，实现**零外部依赖的快速测试**。

### InMemoryKnowledgeFileRepository

- 路径：`src/test/java/.../knowledge/InMemoryKnowledgeFileRepository.java`
- 实现接口：`KnowledgeFileRepository`
- 存储方式：`ConcurrentHashMap`
- 用途：knowledge 模块 Property 测试中替代 MySQL

### InMemoryBindingRepository

- 路径：`src/test/java/.../knowledge/InMemoryBindingRepository.java`
- 实现接口：`SkillKnowledgeBindingRepository`
- 存储方式：`ConcurrentHashMap`
- 用途：测试 Skill 与知识库绑定逻辑

### NoOpCacheService（测试版）

- 路径：`src/test/java/.../knowledge/NoOpCacheService.java`
- 实现接口：`CacheService`
- 用途：在不依赖 Redis 的情况下提供简单内存缓存

> **设计原则**：所有 Property-based 测试均使用 InMemory 实现，无需启动 MySQL / Redis，保证测试**快速、可重复、无副作用**。

---

## 数据库相关测试

### 本地 SQLite 测试

SQLite 数据库由客户端自动创建，初始化脚本位于：

```
src/main/resources/sql/init-local.sql
```

包含以下表：

| 表名 | 用途 |
|------|------|
| `local_session` | 本地会话缓存 |
| `chat_session` | 对话会话 |
| `chat_message` | 对话历史 |
| `favorite` | 用户收藏 |
| `skill_usage` | Skill 使用记录 |
| `settings` | 用户设置 |

相关测试：`ChatRepositoryImplTest`、`SessionRepositoryImplTest`、`FavoriteRepositoryImplTest` 等。

### MySQL 集成测试

MySQL 初始化脚本位于：

```
src/main/resources/sql/init-mysql.sql
```

包含以下表：

| 表名 | 用途 |
|------|------|
| `user` | 用户账号 |
| `skill` | Skill 配置 |
| `knowledge_file` | 知识库文件 |
| `skill_knowledge_binding` | Skill-知识库关联 |
| `feedback` | 用户反馈 |

运行 MySQL 集成测试前，需确保：

1. MySQL 服务已启动
2. 已创建 `yeahmobi_everything` 数据库
3. 已执行 `init-mysql.sql` 初始化表结构
4. `application.properties` 中的连接信息正确

---

## 常见问题排查

### Q1: 测试因 MySQL 连接失败而报错

**现象**：`MySQLDatabaseManagerTest`、`UserRepositoryImplTest` 等测试抛出连接异常。

**解决**：

1. 确认 MySQL 服务已启动
2. 检查 `application.properties` 中 `mysql.url`、`mysql.username`、`mysql.password` 是否正确
3. 如果只需运行纯单元测试，使用以下命令跳过集成测试：

```bash
mvnw.cmd test -Dtest="!com.yeahmobi.everything.repository.mysql.*"
```

### Q2: Redis 相关测试失败

**现象**：`CacheServiceImplTest` 测试抛出连接异常。

**解决**：

1. 确认 Redis 服务已启动
2. 检查 `redis.host`、`redis.port`、`redis.password` 配置
3. 或直接跳过缓存集成测试：

```bash
mvnw.cmd test -Dtest="!com.yeahmobi.everything.repository.cache.*"
```

### Q3: JavaFX 相关测试失败

**现象**：`KnowledgeControllerTest` 等 UI 测试报 JavaFX 初始化错误。

**解决**：

1. 确保使用 JDK 17+（含 JavaFX 模块支持）
2. 确保 `pom.xml` 中 JavaFX 依赖版本与 JDK 版本匹配
3. 在无图形界面的 CI 环境中，使用 `-Djava.awt.headless=true` 或跳过 UI 测试：

```bash
mvnw.cmd test -Dtest="!com.yeahmobi.everything.ui.*"
```

### Q4: jqwik Property 测试运行很慢

**现象**：Property 测试耗时较长。

**解决**：

jqwik 默认每个 `@Property` 运行 1000 次。开发阶段可临时降低运行次数：

```java
@Property(tries = 100)
void myProperty(@ForAll String input) { ... }
```

> 注意：提交前应恢复默认值以保证测试充分性。

### Q5: 如何只运行不依赖外部服务的测试？

```bash
# 仅运行纯单元测试 + Property 测试（不依赖 MySQL/Redis）
mvnw.cmd test -Dtest="!com.yeahmobi.everything.repository.mysql.*,!com.yeahmobi.everything.repository.cache.*"
```

---

## 附录：推荐的本地开发测试流程

```
1. 拉取代码
   └─→ git clone / git pull

2. 快速验证（无外部依赖）
   └─→ mvnw.cmd test -Dtest="!*.repository.mysql.*,!*.repository.cache.*"

3. 完整验证（需 MySQL + 可选 Redis）
   └─→ mvnw.cmd clean test

4. 构建跨平台 Fat JAR
   └─→ mvnw.cmd clean package -DskipTests
   └─→ 产物: target\yeahmobi-everything-1.0.0-all.jar

5. 本地运行
   └─→ mvnw.cmd javafx:run
   └─→ 或: java -jar target\yeahmobi-everything-1.0.0-all.jar

6. 生成原生安装包（仅限当前系统）
   └─→ copy target\yeahmobi-everything-1.0.0.jar target\libs\
   └─→ mvnw.cmd jpackage:jpackage
   └─→ 产物: target\dist\

7. 全平台安装包
   └─→ git tag v1.0.0 && git push origin v1.0.0
   └─→ CI 自动在 Windows / macOS runner 上构建
   └─→ 产物发布到 GitHub Release
```

---

*本文档最后更新：2026-02-06*
