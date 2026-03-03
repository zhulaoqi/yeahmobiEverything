# 实现计划: Yeahmobi Everything

## 概述

基于 Java 17 + JavaFX 的跨平台 AI 桌面应用，后端使用 MySQL + Redis，客户端本地使用 SQLite。使用 Maven 作为构建工具。按模块递增实现：先搭建项目骨架和基础设施（含数据库初始化），再逐步实现各业务模块，最后完成 UI 集成和打包部署。

## 任务

- [x] 1. 项目初始化与基础设施
  - [x] 1.1 创建 Maven 项目结构，配置 pom.xml（含 JavaFX、sqlite-jdbc、mysql-connector-j、jedis、Gson、flexmark-java、JUnit 5、jqwik、Mockito 依赖），创建 Maven Wrapper（mvnw / mvnw.cmd）
    - 创建标准 Java 项目目录结构（src/main/java、src/main/resources、src/test/java）
    - 按设计文档配置所有依赖项和 jpackage-maven-plugin 插件
    - _Requirements: 10.1, 10.4, 12.1_

  - [x] 1.2 创建公共模块（common 包）
    - 实现 Config.java（读取 application.properties 配置，包含 MySQL、Redis、飞书 OAuth 等配置项）
    - 实现 NetworkException.java（自定义网络异常）
    - 实现 HttpClientUtil.java（封装 Java HttpClient 的 GET/POST 请求）
    - 创建 application.properties 配置文件模板（含 MySQL、Redis、飞书 OAuth 配置）
    - _Requirements: 11.3, 16.6_

  - [x] 1.3 实现客户端本地 SQLite 数据库初始化
    - 实现 LocalDatabaseManager.java（SQLite 连接管理、本地表创建）
    - 创建 src/main/resources/sql/init-local.sql（本地表：local_session、chat_session、chat_message、favorite、skill_usage、settings）
    - _Requirements: 7.4, 16.3_

  - [x] 1.4 实现后端 MySQL 数据库初始化
    - 实现 MySQLDatabaseManager.java（MySQL 连接池管理、表创建）
    - 创建 src/main/resources/sql/init-mysql.sql（后端表：user、skill、knowledge_file、skill_knowledge_binding、feedback）
    - _Requirements: 16.1_

  - [x] 1.5 实现 Redis 缓存模块
    - 实现 RedisManager.java（Redis 连接管理，基于 Jedis）
    - 实现 CacheService.java 接口和实现（会话缓存、Skill 列表缓存、知识库文本缓存、缓存失效）
    - _Requirements: 16.2, 16.4, 16.5_

  - [x] 1.6 编写 LocalDatabaseManager 和 MySQLDatabaseManager 单元测试
    - 测试数据库初始化、表创建、连接管理
    - _Requirements: 7.4, 16.1_

  - [x]* 1.7 编写 Redis 缓存 round-trip 属性测试
    - **Property 35: Redis 会话缓存 round-trip**
    - **Validates: Requirements 16.5**

- [x] 2. 认证模块（飞书 OAuth + 邮箱密码登录）
  - [x] 2.1 实现认证数据层
    - 实现 Session.java（record 类，含 token、userId、username、email、loginType、expiresAt）
    - 实现 AuthResult.java（record 类）
    - 实现 SessionRepository.java 接口和 SQLite 实现（本地会话缓存）
    - 实现 UserRepository.java 接口和 MySQL 实现（用户账号 CRUD）
    - _Requirements: 1.10, 16.1_

  - [x] 2.2 实现邮箱密码登录和注册服务
    - 实现 AuthService.java 接口
    - 实现 AuthServiceImpl.java 中的 loginWithEmail（邮箱密码验证）
    - 实现 sendVerificationCode（发送邮箱验证码）
    - 实现 register（验证码校验、创建用户账号、自动登录）
    - 实现 logout（清除本地会话和 Redis 缓存）
    - _Requirements: 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.11_

  - [x] 2.3 实现飞书 OAuth 登录服务
    - 实现 FeishuOAuthService.java（生成授权 URL、授权码换取 access_token、获取用户信息）
    - 实现 AuthServiceImpl.java 中的 loginWithFeishu 和 getFeishuOAuthUrl
    - 登录成功后将会话存储到 Redis
    - _Requirements: 1.2, 1.3, 16.5_

  - [x]* 2.4 编写邮箱密码登录属性测试
    - **Property 1: 邮箱密码登录结果与凭证有效性一致**
    - **Validates: Requirements 1.4, 1.5**

  - [x]* 2.5 编写会话存储属性测试
    - **Property 2: 会话存储 round-trip**
    - **Validates: Requirements 1.10**

  - [x]* 2.6 编写退出登录属性测试
    - **Property 3: 退出登录清除会话**
    - **Validates: Requirements 1.11**

  - [x]* 2.7 编写注册后登录 round-trip 属性测试
    - **Property 31: 注册后登录 round-trip**
    - **Validates: Requirements 1.8**

  - [x]* 2.8 编写无效验证码拒绝注册属性测试
    - **Property 32: 无效验证码拒绝注册**
    - **Validates: Requirements 1.9**

- [x] 3. Skill 模块
  - [x] 3.1 实现 Skill 数据模型和服务层
    - 实现 Skill.java（record 类，含 SkillType 和 SkillKind 字段）
    - 实现 SkillType.java 和 SkillKind.java 枚举
    - 实现 SkillRepository.java 接口和 MySQL 实现
    - 实现 SkillService.java 接口和 SkillServiceImpl.java（获取 Skill 列表时优先查 Redis 缓存、搜索、分类筛选、类型筛选、默认 Skill）
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.7, 16.2_

  - [x] 3.2 实现收藏和最近使用功能
    - 实现 FavoriteRepository.java 接口和 SQLite 实现
    - 实现 UsageRepository.java 接口和 SQLite 实现
    - 在 SkillService 中集成收藏和最近使用逻辑
    - _Requirements: 2.5, 2.6_

  - [x]* 3.3 编写 Skill 搜索过滤属性测试
    - **Property 4: Skill 搜索过滤正确性**
    - **Validates: Requirements 2.3**

  - [x]* 3.4 编写 Skill 分类筛选属性测试
    - **Property 5: Skill 分类筛选正确性**
    - **Validates: Requirements 2.4**

  - [x]* 3.5 编写收藏 round-trip 属性测试
    - **Property 6: 收藏 round-trip**
    - **Validates: Requirements 2.5**

  - [x]* 3.6 编写最近使用排序属性测试
    - **Property 7: 最近使用排序**
    - **Validates: Requirements 2.6**

- [x] 4. 检查点 - 确保所有测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 5. 对话模块
  - [x] 5.1 实现对话数据层
    - 实现 ChatMessage.java、ChatSession.java、ChatResponse.java（record 类）
    - 实现 ChatRepository.java 接口和 SQLite 实现（saveMessage、getHistory、getAllSessions、searchSessions、deleteSession）
    - _Requirements: 3.6, 7.1, 7.2, 8.2, 8.4, 8.5_

  - [x] 5.2 实现对话服务层
    - 实现 ChatService.java 接口和 ChatServiceImpl.java
    - 实现 sendMessage 方法（构造大模型 API 请求，包含用户输入和 Skill 上下文，知识型 Skill 自动注入知识库内容）
    - 实现 buildContextWithKnowledge（从 Redis 缓存或 MySQL 获取知识库文本）
    - 实现异步调用（CompletableFuture），超时 30 秒
    - _Requirements: 3.2, 3.4, 3.5, 13.4_

  - [x]* 5.3 编写大模型请求构造属性测试
    - **Property 8: 大模型请求构造完整性**
    - **Validates: Requirements 3.2**

  - [x]* 5.4 编写对话历史 round-trip 属性测试
    - **Property 9: 对话历史 round-trip**
    - **Validates: Requirements 3.6, 7.1, 7.2**

  - [x]* 5.5 编写历史记录排序属性测试
    - **Property 16: 历史记录时间倒序**
    - **Validates: Requirements 8.2**

  - [x]* 5.6 编写历史记录搜索属性测试
    - **Property 17: 历史记录搜索正确性**
    - **Validates: Requirements 8.4**

  - [x]* 5.7 编写历史记录删除属性测试
    - **Property 18: 历史记录删除**
    - **Validates: Requirements 8.5**

- [x] 6. 反馈与通知模块
  - [x] 6.1 实现反馈服务和飞书通知
    - 实现 Feedback.java、FeedbackResult.java（record 类）
    - 实现 FeedbackRepository.java 接口和 MySQL 实现
    - 实现 FeedbackService.java 接口和 FeedbackServiceImpl.java（提交反馈到 MySQL）
    - 实现 FeishuNotifier.java 接口和 FeishuNotifierImpl.java（构造飞书卡片消息、通过 Webhook 发送）
    - _Requirements: 4.1, 4.2, 4.3, 4.6_

  - [x]* 6.2 编写飞书通知消息完整性属性测试
    - **Property 10: 飞书通知消息完整性**
    - **Validates: Requirements 4.3, 4.6**

  - [x]* 6.3 编写反馈提交单元测试
    - 测试提交成功和失败场景，使用 Mockito mock HTTP 调用
    - _Requirements: 4.4, 4.5_

- [x] 7. 管理端模块
  - [x] 7.1 实现管理端服务层
    - 实现 SkillAdmin.java、SkillIntegrationResult.java（record 类）
    - 实现 SkillCommandParser.java（解析命令行集成命令）
    - 实现 AdminService.java 接口和 AdminServiceImpl.java（Skill 管理、反馈查看、状态更新）
    - Skill 变更操作后调用 CacheService.invalidateSkillCache() 清除缓存
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 16.4_

  - [x]* 7.2 编写命令解析 round-trip 属性测试
    - **Property 11: Skill 集成命令解析 round-trip**
    - **Validates: Requirements 5.3**

  - [x]* 7.3 编写 Skill 状态切换属性测试
    - **Property 12: Skill 状态切换一致性**
    - **Validates: Requirements 5.4**

  - [x]* 7.4 编写反馈列表排序属性测试
    - **Property 13: 反馈列表时间倒序**
    - **Validates: Requirements 5.5**

  - [x]* 7.5 编写反馈状态更新属性测试
    - **Property 14: 反馈状态更新**
    - **Validates: Requirements 5.6**

  - [x]* 7.6 编写 Skill 变更后缓存失效属性测试
    - **Property 36: Skill 变更后缓存失效**
    - **Validates: Requirements 16.4**

- [x] 8. 知识库模块
  - [x] 8.1 实现知识库数据层和服务层
    - 实现 KnowledgeFile.java（record 类，含 sourceType 字段）
    - 实现 KnowledgeFileRepository.java 接口和 MySQL 实现
    - 实现 SkillKnowledgeBindingRepository.java 接口和 MySQL 实现
    - 实现 KnowledgeBaseService.java 接口和 KnowledgeBaseServiceImpl.java
    - 实现文件上传（单个和批量）、手动输入创建、文本提取、文件格式验证
    - 实现绑定/解绑、合并知识库文本（优先从 Redis 缓存获取）
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7, 14.1, 14.2, 14.3, 14.4, 14.5, 14.6_

  - [x]* 8.2 编写知识库文件绑定 round-trip 属性测试
    - **Property 19: 知识库文件绑定 round-trip**
    - **Validates: Requirements 13.1, 13.5**

  - [x]* 8.3 编写知识库文件解绑属性测试
    - **Property 20: 知识库文件解绑正确性**
    - **Validates: Requirements 13.4**

  - [x]* 8.4 编写知识库文件格式验证属性测试
    - **Property 21: 知识库文件格式验证**
    - **Validates: Requirements 13.2, 14.5**

  - [x]* 8.5 编写知识库上下文构建属性测试
    - **Property 22: 知识库上下文构建完整性**
    - **Validates: Requirements 13.3**

  - [x]* 8.6 编写文本提取 round-trip 属性测试
    - **Property 23: 文本提取 round-trip**
    - **Validates: Requirements 13.6, 14.2**

  - [x]* 8.7 编写知识库文件更新保留绑定属性测试
    - **Property 24: 知识库文件更新保留绑定**
    - **Validates: Requirements 14.3**

  - [x]* 8.8 编写知识库文件删除级联属性测试
    - **Property 25: 知识库文件删除级联**
    - **Validates: Requirements 14.4**

  - [x]* 8.9 编写批量上传文件数量一致性属性测试
    - **Property 33: 批量上传文件数量一致性**
    - **Validates: Requirements 15.4**

  - [x]* 8.10 编写手动输入知识内容 round-trip 属性测试
    - **Property 34: 手动输入知识内容 round-trip**
    - **Validates: Requirements 15.5**

- [x] 9. Skill 模板化创建与知识库 Skill 创建向导
  - [x] 9.1 实现 Skill 模板化创建服务
    - 实现 SkillTemplate.java 和 KnowledgeSkillTemplate.java（record 类）
    - 实现 AdminService 中的 createSkillFromTemplate（通用型 Skill 创建）
    - 实现 AdminService 中的 createKnowledgeSkill（知识型 Skill 创建，自动绑定知识库文件）
    - 实现 Prompt 模板渲染预览功能
    - 创建成功后清除 Skill 缓存
    - _Requirements: 15.1, 15.2, 15.3, 15.6, 15.7, 15.8, 15.9, 15.10, 15.11, 15.12_

  - [x]* 9.2 编写 Skill 模板创建 round-trip 属性测试
    - **Property 26: Skill 模板创建 round-trip**
    - **Validates: Requirements 15.6, 15.10, 15.11**

  - [x]* 9.3 编写 Skill 模板必填字段验证属性测试
    - **Property 27: Skill 模板必填字段验证**
    - **Validates: Requirements 15.7**

  - [x]* 9.4 编写模板创建自动绑定知识库属性测试
    - **Property 28: 模板创建自动绑定知识库**
    - **Validates: Requirements 15.8**

  - [x]* 9.5 编写 Prompt 模板渲染属性测试
    - **Property 29: Prompt 模板渲染完整性**
    - **Validates: Requirements 15.9**

  - [x]* 9.6 编写 Skill 类型筛选属性测试
    - **Property 30: Skill 类型筛选正确性**
    - **Validates: Requirements 17.2**

- [x] 10. 设置模块
  - [x] 10.1 实现设置数据层
    - 实现 SettingsRepository.java 接口和 SQLite 实现（saveSetting、getSetting）
    - _Requirements: 7.3_

  - [x]* 10.2 编写用户设置 round-trip 属性测试
    - **Property 15: 用户设置 round-trip**
    - **Validates: Requirements 7.3**

- [x] 11. 检查点 - 确保所有业务逻辑测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 12. UI 层 - 样式与布局基础
  - [x] 12.1 创建 JavaFX CSS 主题文件
    - 创建 src/main/resources/css/light-theme.css（浅色主题：GitHub 风格，白色/浅灰背景、绿色主操作、蓝色强调）
    - 创建 src/main/resources/css/dark-theme.css（深色主题：深灰/深蓝背景）
    - 定义通用样式类：卡片、按钮、输入框、导航栏、对话气泡
    - _Requirements: 9.1, 9.2, 9.5_

  - [x] 12.2 创建主窗口布局（main.fxml）
    - 实现左侧导航栏（Skill 集合、历史记录、收藏、反馈、设置、管理员入口）
    - 实现右侧内容区域（StackPane 用于页面切换）
    - 实现 MainController.java（导航切换逻辑、主题切换）
    - _Requirements: 9.3_

- [x] 13. UI 层 - 登录与注册页面
  - [x] 13.1 创建登录界面（login.fxml + AuthController.java）
    - 实现登录页面：飞书登录按钮 + 邮箱密码登录表单 + 注册入口
    - 实现飞书登录逻辑（打开系统浏览器跳转飞书授权页面，监听回调获取授权码）
    - 实现邮箱密码登录逻辑（调用 AuthService.loginWithEmail）
    - 实现自动登录检查（启动时检查本地 Session 和 Redis 缓存）
    - 实现登录页面品牌宣传信息和视觉设计
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.10, 1.12_

  - [x] 13.2 创建注册界面（register.fxml）
    - 实现注册表单：邮箱输入框、发送验证码按钮、验证码输入框、密码设置框
    - 实现发送验证码逻辑（调用 AuthService.sendVerificationCode）
    - 实现注册提交逻辑（调用 AuthService.register）
    - 实现验证码错误提示和重新发送
    - _Requirements: 1.6, 1.7, 1.8, 1.9_

- [x] 14. UI 层 - Skill 集合页面
  - [x] 14.1 创建 Skill 集合页面（skill-list.fxml + SkillController.java）
    - 实现搜索栏、分类筛选标签、类型筛选（全部/通用/内部）
    - 实现"我的收藏"区域、"最近使用"区域
    - 实现 Skill 卡片网格布局（FlowPane），每个卡片显示名称、图标、分类标签、描述、内部 Skill 公司标识徽章
    - 实现收藏按钮点击逻辑
    - 实现 Skill 点击进入对话界面
    - _Requirements: 2.1, 2.3, 2.4, 2.5, 2.6, 2.8, 17.1, 17.2, 17.5_

- [x] 15. UI 层 - 对话页面
  - [x] 15.1 创建对话界面（chat.fxml + ChatController.java）
    - 实现气泡式聊天布局（用户消息靠右蓝色、AI 回复靠左灰色）
    - 实现消息输入框和发送按钮
    - 实现加载指示器（大模型处理中）
    - 实现 Markdown 渲染（使用 flexmark-java 将 AI 回复转为 HTML，通过 WebView 展示）
    - 实现 Skill 使用说明查看入口，显示 Skill 类型标识（通用/内部）
    - 实现返回按钮
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.7, 3.8, 9.4, 17.3_

- [x] 16. UI 层 - 历史记录页面
  - [x] 16.1 创建历史记录页面（history.fxml + HistoryController.java）
    - 实现会话列表（ListView），每条显示 Skill 名称、最后消息摘要、时间
    - 实现搜索功能
    - 实现点击会话加载完整对话
    - 实现删除会话功能
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

- [x] 17. UI 层 - 反馈与设置页面
  - [x] 17.1 创建反馈页面（feedback.fxml + FeedbackController.java）
    - 实现反馈内容输入框和提交按钮
    - 实现提交成功/失败提示
    - _Requirements: 4.1, 4.2, 4.4, 4.5_

  - [x] 17.2 创建设置页面（settings.fxml + SettingsController.java）
    - 实现主题切换（浅色/深色）
    - 实现退出登录按钮
    - _Requirements: 1.11, 9.2_

- [x] 18. UI 层 - 管理端页面（含知识库 Skill 创建向导）
  - [x] 18.1 创建管理端主页面（admin.fxml + AdminController.java）
    - 实现 Skill 管理列表（名称、状态、类型、驱动类型、描述）
    - 实现命令行输入区域（TextArea + 执行按钮）
    - 实现 Skill 启用/禁用切换
    - 实现反馈列表查看（时间倒序、标记已处理）
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

  - [x] 18.2 创建 Skill 创建向导页面（skill-wizard.fxml + SkillWizardController.java）
    - 实现第一步：选择 Skill 类型（通用型 / 知识型）
    - 实现通用型 Skill 创建表单（名称、描述、分类、图标、Prompt 模板）
    - 实现知识型 Skill 创建向导分步流程：上传文档/手动输入 → 配置基本信息 → 配置 Prompt 模板 → 预览测试 → 发布
    - 实现批量文件上传组件（支持拖拽上传，显示处理进度）
    - 实现手动输入知识内容的富文本编辑器
    - 实现 Prompt 模板预览功能
    - 实现 Skill 类型选择（通用/内部）
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 15.9, 15.11, 17.4_

  - [x] 18.3 创建知识库管理页面（knowledge.fxml + KnowledgeController.java）
    - 实现知识库文件列表（文件名、类型、大小、上传时间、关联 Skill）
    - 实现文件上传、更新、删除功能
    - 实现手动输入知识内容的编辑功能
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6_

- [x] 19. 检查点 - UI 集成验证
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 20. 跨平台适配与应用入口
  - [x] 20.1 实现应用入口和跨平台适配
    - 实现 App.java（JavaFX Application 入口，初始化本地 SQLite、连接 MySQL/Redis、加载配置、启动主窗口）
    - 实现系统托盘适配（Windows 使用 SystemTray、macOS 适配 Dock 行为）
    - 设置最小窗口尺寸 800x600
    - _Requirements: 6.1, 6.2, 6.4_
