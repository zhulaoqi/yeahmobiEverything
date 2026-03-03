# Yeahmobi Everything

> 面向公司全体员工的 AI 桌面应用 —— 基于 Skill 的自然语言交互平台

## 项目简介

Yeahmobi Everything 是一款跨平台（Windows / macOS）AI 桌面应用。用户可以根据自身岗位需求选择不同的 Skill（技能），通过自然语言与大模型交互来完成翻译、写作、编程、数据分析等工作任务。

核心特性：
- Skill 集合展示、搜索、分类筛选、收藏、最近使用
- 基于大模型的自然语言对话，支持上下文连续对话
- 知识库 RAG：管理员可为 Skill 绑定知识库文件（PDF/MD/TXT），对话时自动注入知识上下文
- 双模式登录：邮箱密码 + 飞书 OAuth 2.0
- 邮箱验证码注册（SMTP 真实发送）
- 管理端：Skill 创建向导、知识库管理、反馈处理
- 个人 Skill：用户私有创建、导出技能包、提交管理员审核
- 技能市场：公开技能一键加入个人技能库
- 审核通过后同步到公共技能库（可被全量用户使用）
- AgentScope Server：为技能树与智能体运行提供可扩展服务端
- 浅色/深色主题切换
- 系统托盘集成（Windows 最小化到托盘，macOS Dock 适配）

## 技术架构

### 多模块结构

项目采用 Maven 多模块结构，服务端和客户端独立打包：

```
yeahmobi-everything/                  (Parent POM)
├── pom.xml                            公共依赖管理
├── everything-server/                 服务端模块
│   ├── pom.xml
│   └── src/main/java/.../
│       ├── auth/                      认证（邮箱登录、飞书 OAuth、SMTP 邮件）
│       ├── chat/                      对话（LLM API 交互）
│       ├── skill/                     Skill 管理
│       ├── admin/                     管理端
│       ├── knowledge/                 知识库
│       ├── feedback/                  反馈
│       ├── notification/              飞书通知
│       ├── personalskill/             个人 Skill（私有 + 审核）
│       ├── common/                    公共工具（Config、HttpClient）
│       └── repository/               数据访问（SQLite / MySQL / Redis）
├── everything-agentscope-server/      AgentScope Server
│   ├── pom.xml
│   └── src/main/java/.../
│       └── agentscope/                AgentScope 执行服务
├── everything-client/                 客户端模块
│   ├── pom.xml
│   └── src/main/java/.../
│       ├── App.java                   JavaFX 应用入口
│       ├── Launcher.java              Fat JAR 启动入口
│       └── ui/                        JavaFX 控制器
│   └── src/main/resources/
│       ├── fxml/                      FXML 布局
│       ├── css/                       主题样式
│       └── images/                    图标资源
├── .github/workflows/build.yml        CI/CD 自动构建
├── README.md
├── PRODUCT.md                         产品价值文档
└── TESTING.md                         测试文档
```

| 模块 | 职责 | 产物 |
|------|------|------|
| **everything-server** | 认证、邮件、数据库、缓存、业务逻辑 | `everything-server-1.0.0.jar` |
| **everything-client** | JavaFX UI、控制器、FXML/CSS | `everything-client-1.0.0.jar` + Fat JAR + .dmg/.msi |
| **everything-agentscope-server** | AgentScope Skill 执行服务 | `everything-agentscope-server-1.0.0.jar` |

### 架构概览

```
┌─────────────────────────────────────────────────────────┐
│           everything-client (JavaFX Desktop App)         │
├──────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────┐  │
│  │             UI 层 (JavaFX FXML + CSS)              │  │
│  │  AuthController │ MainController │ ChatController  │  │
│  │  SkillController │ AdminController │ ...           │  │
│  └────────────────────────────────────────────────────┘  │
│                           ↓                              │
├──────────────────────────────────────────────────────────┤
│           everything-server (Backend Services)           │
├──────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────┐  │
│  │           服务层 (Business Logic)                   │  │
│  │  AuthService │ SkillService │ ChatService          │  │
│  │  EmailService │ AdminService │ KnowledgeBaseService│  │
│  └────────────────────────────────────────────────────┘  │
│                           ↓                              │
│  ┌────────────────────────────────────────────────────┐  │
│  │           数据访问层 (Repository)                    │  │
│  │  SQLite (本地) │ MySQL (后端) │ Redis (缓存)       │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
                           ↓
      ┌────────────────────┼────────────────────┐
      ↓                    ↓                    ↓
 ┌──────────┐        ┌──────────┐         ┌──────────┐
 │ LLM API  │        │  飞书    │         │  SMTP    │
 │ (大模型) │        │ OAuth +  │         │ 邮件服务 │
 │          │        │ Webhook  │         │          │
 └──────────┘        └──────────┘         └──────────┘
```

### 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 17+ |
| UI 框架 | JavaFX | 21.0.5 |
| 构建工具 | Maven (多模块) | 3.x (Maven Wrapper) |
| 本地数据库 | SQLite | 3.45.1.0 |
| 后端数据库 | MySQL | 8.x |
| 缓存 | Redis | 6.x+ (Jedis 5.1.0) |
| 邮件 | Jakarta Mail (Angus) | 2.0.3 |
| JSON | Gson | 2.10.1 |
| Markdown 渲染 | flexmark-java | 0.64.8 |
| 打包 | jpackage | JDK 17 内置 |
| 测试 | JUnit 5 + jqwik + Mockito | 5.10.2 / 1.8.3 / 5.10.0 |
| 智能体 | AgentScope Java | 1.0.8 |

## 快速开始

### 1. 前置准备

**必需**
- JDK 17+（包含 `jpackage`）
- MySQL 8.x
- LLM API 访问凭据

**可选**
- Redis 6.x+（缓存加速，可降级）
- 飞书开放平台应用（OAuth 登录 + Webhook 通知）
- 飞书管理员私信能力（审核通知）
- SMTP 邮箱（验证码发送，未配置则降级为控制台输出）

### 2. 数据库初始化

```bash
mysql -u root -p
```

```sql
CREATE DATABASE yeahmobi_everything CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SOURCE everything-server/src/main/resources/sql/init-mysql.sql;
```

### 3. 配置

编辑 `everything-server/src/main/resources/application.properties`：

```properties
# LLM API（必填）
llm.api.url=https://your-llm-api-endpoint/v1
llm.api.key=YOUR_API_KEY

# MySQL（必填）
mysql.url=jdbc:mysql://localhost:3306/yeahmobi_everything?useSSL=false&characterEncoding=utf8mb4
mysql.username=root
mysql.password=YOUR_MYSQL_PASSWORD

# SMTP 邮件（推荐，用于发送验证码）
smtp.host=smtp.qq.com
smtp.port=465
smtp.username=your-email@qq.com
smtp.password=YOUR_SMTP_AUTHORIZATION_CODE
smtp.from=your-email@qq.com
smtp.ssl=true

# Redis（可选）
redis.host=localhost
redis.port=6379

# 飞书（可选）
feishu.oauth.app_id=YOUR_FEISHU_APP_ID
feishu.oauth.app_secret=YOUR_FEISHU_APP_SECRET
feishu.oauth.redirect_uri=http://localhost:8080/auth/feishu/callback
feishu.admin.user_id=YOUR_ADMIN_UNION_OR_OPEN_ID
feishu.admin.user_id_type=union_id

# AgentScope Server（可选）
agentscope.enabled=false
agentscope.api.url=https://dashscope.aliyuncs.com/compatible-mode/v1
agentscope.api.key=YOUR_AGENT_API_KEY
agentscope.model=qwen-max
agentscope.server.port=8099
```

> **SMTP 授权码获取**：QQ 邮箱 → 设置 → 账户 → POP3/SMTP 服务 → 开启 → 生成授权码

### 4. 构建与运行

```bash
# 首次构建：安装 server 模块到本地 Maven 仓库
./mvnw clean install -DskipTests

# 后续构建：使用 verify 触发 antrun 插件复制 JARs
./mvnw clean verify -DskipTests

# 运行 Fat JAR
java -jar everything-client/target/yeahmobi-everything-1.0.0-all.jar
```

> **重要**：多模块项目首次构建请使用 `install` 确保依赖模块正确安装到本地仓库。

## 客户端打包与分发

### 方式一：跨平台 Fat JAR（一次构建，全平台可用）

```bash
# 首次构建
./mvnw clean install -DskipTests

# 后续构建
./mvnw clean verify -DskipTests
```

产物：`everything-client/target/yeahmobi-everything-1.0.0-all.jar`

## AgentScope Server（可选）

当需要独立的智能体服务端时，启动 AgentScope Server：

```bash
./mvnw -pl everything-agentscope-server -am -DskipTests package
java -jar everything-agentscope-server/target/everything-agentscope-server-1.0.0.jar
```

默认端口：`8099`，可通过 `agentscope.server.port` 修改。

接口示例：

- 单步执行：`POST /api/agentscope/execute`
- 多智能体链路：`POST /api/agentscope/multi-agent/execute`

请求体：

```json
{
  "input": "用户需求或任务描述",
  "promptTemplate": "可选模板，支持 {{input}}"
}
```

## 个人 Skill 创建与审核

1. 进入侧边栏 `个人 Skill` 页面，填写技能信息并保存草稿  
2. 点击 `提交审核`，系统会私信通知管理员  
3. 管理员在 `管理后台 → 个人 Skill 审核` 中通过或驳回  
4. 审核通过后自动同步到公共技能库（全量用户可见）

## 技能市场

在侧边栏进入 `技能市场`：
- 浏览公开技能
- 一键添加到个人技能库（保存为草稿，可再编辑）

## Anthropic Skills 全量导入（默认包含）

本项目支持从本地克隆的 Anthropic Skills 仓库导入全部技能：

1. 先克隆仓库（本地路径随意）  
2. 在 `application.properties` 设置路径并开启自动导入  

```properties
skills.anthropic.auto_import=true
skills.anthropic.path=/path/to/anthropics/skills
```

首次启动会自动导入 `SKILL.md` 内容并生成 Skill 介绍与 Prompt 模板。  
技能说明来源：[anthropics/skills](https://github.com/anthropics/skills)

## Skill 技能包文件规范

个人 Skill 支持导出 JSON 技能包（私有格式 v1.0）：

```json
{
  "schemaVersion": "1.0",
  "type": "personal-skill",
  "id": "uuid",
  "ownerUserId": "user-id",
  "name": "技能名称",
  "description": "一句话描述价值",
  "category": "分类",
  "promptTemplate": "必须包含 {{input}}",
  "createdAt": 0,
  "updatedAt": 0
}
```

校验规则：
- `name`/`description`/`category`/`promptTemplate` 必填  
- `promptTemplate` 必须包含 `{{input}}` 或 `{{user_input}}`

分发后，用户在任何安装了 JDK 17+ 的机器上直接运行：
```bash
java -jar yeahmobi-everything-1.0.0-all.jar
```

### 方式二：原生安装包（.dmg / .msi）

#### 本地构建

`jpackage` 只能为当前系统生成安装包（macOS 生成 `.dmg`，Windows 生成 `.msi`）。

**macOS：**
```bash
# 首次构建（安装依赖模块 + 准备 libs 目录）
./mvnw clean install -DskipTests
./mvnw verify -DskipTests

# 后续构建
./mvnw clean verify -DskipTests

# 生成 .dmg
./mvnw jpackage:jpackage -pl everything-client

# 产物
everything-client/target/dist/Yeahmobi Everything-1.0.0.dmg
```

**Windows：**
```cmd
REM 首次构建
mvnw.cmd clean install -DskipTests
mvnw.cmd verify -DskipTests

REM 后续构建
mvnw.cmd clean verify -DskipTests

REM 生成 .msi（需要 WiX Toolset 3.x）
mvnw.cmd jpackage:jpackage -pl everything-client

REM 产物
everything-client\target\dist\Yeahmobi Everything-1.0.0.msi
```

> Windows 上生成 `.msi` 需要安装 [WiX Toolset 3.x](https://wixtoolset.org/releases/) 并加入 PATH。

#### CI/CD 自动构建（推荐）

项目已配置 GitHub Actions（`.github/workflows/build.yml`），推送 tag 后自动在多平台构建：

```bash
git tag v1.0.0
git push origin v1.0.0
```

CI 会自动在三个平台构建并创建 Release：

| 产物 | 说明 |
|------|------|
| `yeahmobi-everything-1.0.0-all.jar` | 跨平台 Fat JAR |
| `Yeahmobi Everything-1.0.0.msi` | Windows 安装包 |
| `Yeahmobi Everything-1.0.0.dmg` | macOS 安装包 |

### 分发方式对比

| | Fat JAR | 原生安装包 |
|---|---|---|
| **构建环境** | 任意一台机器 | 需要目标 OS 或 CI/CD |
| **用户前置要求** | JDK 17+ | 无，自带运行时 |
| **安装体验** | 双击 JAR 或命令行启动 | 系统级安装（快捷方式、Dock 图标） |
| **适用阶段** | 开发 / 内测 | 正式发布 |

## 常用构建命令

```bash
# 编译全部
./mvnw compile

# 只编译 server
./mvnw compile -pl everything-server

# 只编译 client（含 server 依赖）
./mvnw compile -pl everything-client -am

# 打包全部（Fat JAR + 模块 JAR）
./mvnw package -DskipTests

# 只打包 server
./mvnw package -pl everything-server -DskipTests

# 生成原生安装包（需先 package）
./mvnw jpackage:jpackage -pl everything-client

# 运行测试
./mvnw test

# 开发模式运行（JavaFX 插件）
./mvnw javafx:run -pl everything-client
```

## 飞书 OAuth 登录说明

飞书登录采用 OAuth 2.0 授权码模式。点击「飞书登录」后：

1. 应用在本地 **8080 端口**启动轻量 HTTP 服务器
2. 系统浏览器打开飞书授权页面
3. 用户在飞书完成授权
4. 飞书回调 `http://localhost:8080/auth/feishu/callback?code=xxx`
5. 本地服务器接收 code，完成登录，浏览器显示「授权成功」
6. 服务器自动关闭

## 依赖汇总

| 依赖 | 必需/可选 | 用途 |
|------|----------|------|
| JDK 17+ | 必需 | 编译和运行 |
| Maven 3.x | 必需 | 构建（已内置 Maven Wrapper） |
| MySQL 8.x | 必需 | 用户账号、Skill 配置、知识库、反馈 |
| Redis 6.x+ | 可选 | 会话缓存、Skill 列表缓存、知识库缓存 |
| SMTP 邮箱 | 推荐 | 注册验证码发送（未配置则控制台输出） |
| 飞书开放平台 | 可选 | 飞书 OAuth 登录 + Webhook 通知 |
| LLM API | 必需 | 大模型对话服务 |

## License

Internal use only - Yeahmobi
