# 需求文档

## 简介

Yeahmobi Everything 是一款面向公司全体员工的 AI 桌面应用程序。该应用以 Skill（技能）集合为核心，用户可以根据自身岗位需求选择对应的 Skill，通过自然语言与大模型交互来完成各类工作任务。应用支持 Windows 和 macOS 双平台，使用 Java 作为主要开发语言。

## 术语表

- **App**: Yeahmobi Everything 桌面应用程序
- **Skill**: 一个可集成的功能模块，代表特定的 AI 能力，用户通过选择 Skill 来执行对应类型的任务
- **Skill_集合**: App 中所有可用 Skill 的展示页面
- **用户**: 公司内部使用 App 的员工
- **管理员**: 拥有管理权限的员工，负责 Skill 管理和反馈处理
- **管理端**: 管理员专用的管理界面，用于 Skill 集成和系统配置
- **反馈系统**: 用户提交需求或问题的功能模块，支持将消息发送至飞书
- **飞书**: 公司内部使用的即时通讯和协作平台
- **大模型**: 集成的大语言模型服务，用于理解用户自然语言并执行任务
- **自然语言交互**: 用户以日常语言描述需求，由大模型解析并执行的交互方式
- **Skill_分类**: Skill 的归类标签，用于将 Skill 按功能领域组织（如"翻译"、"写作"、"开发"等）
- **Skill_使用说明**: 每个 Skill 附带的详细说明页面，描述功能、使用方法和示例
- **历史记录**: 用户使用 Skill 产生的对话成果和交互记录的集合
- **收藏**: 用户将常用 Skill 标记为收藏，方便快速访问
- **默认_Skill**: App 预置的常用 Skill，开箱即用
- **知识库**: 与 Skill 关联的文件集合（PDF、Word、Markdown、TXT 等），大模型回答时参考这些文件作为上下文
- **知识库文件**: 上传到系统中的单个文档文件，作为 Skill 的参考知识来源
- **内部_Skill**: 由公司内部创建和维护的 Skill，带有公司标识，区别于通用 Skill
- **通用_Skill**: 面向所有用户的标准 Skill，不带公司内部标识
- **通用型_Skill**: 纯 Prompt 驱动的 Skill，不依赖知识库，通过 Prompt_模板 定义行为
- **知识型_Skill**: Prompt + 知识库 RAG 驱动的 Skill，对话时从知识库中检索相关内容增强回答
- **Skill_模板**: 管理员通过表单快速创建 Skill 的预定义结构，包含名称、描述、分类、知识库文件和 prompt 模板
- **Prompt_模板**: Skill 与大模型交互时使用的提示词模板，定义 Skill 的行为和回答风格
- **RAG**: 检索增强生成（Retrieval-Augmented Generation），对话时从知识库中检索相关文档片段，作为上下文注入大模型请求
- **向量化**: 将文本内容转换为数值向量的过程，用于语义相似度检索
- **文档分块**: 将知识库文件内容按段落或固定长度切分为多个文本块，便于向量化和检索
- **Skill_创建向导**: 管理端提供的可视化分步创建流程：填写基本信息 → 上传知识库文档 → 配置 Prompt 模板 → 预览测试 → 发布
- **飞书_OAuth**: 通过飞书 OAuth 2.0 协议进行用户身份认证的登录方式
- **邮箱密码登录**: 用户通过注册邮箱和密码进行身份认证的登录方式
- **邮箱验证码**: 注册流程中发送到用户邮箱的一次性验证码，用于验证邮箱所有权
- **MySQL**: 后端关系型数据库，用于存储用户账号、Skill 配置、知识库元数据、反馈记录等业务数据
- **Redis**: 内存缓存数据库，用于缓存会话状态、Skill 列表、热点数据等，提升访问性能
- **手动输入知识内容**: 管理员在创建知识型_Skill 时，通过富文本编辑器直接输入知识内容（如 FAQ、流程手册），而非上传文件

## 需求

### 需求 1：用户认证（双模式登录）

**用户故事：** 作为公司员工，我希望通过飞书登录或邮箱密码登录来验证身份，以便安全地使用 App 的所有功能。

#### 验收标准

1. WHEN 用户启动 App THEN App SHALL 显示登录界面，提供"飞书登录"和"邮箱密码登录"两种登录方式
2. WHEN 用户点击"飞书登录"按钮 THEN App SHALL 跳转至飞书 OAuth 2.0 授权页面
3. WHEN 用户在飞书授权页面完成授权 THEN App SHALL 使用授权码换取用户信息，创建会话并跳转至 Skill_集合 主页面
4. WHEN 用户输入有效的邮箱和密码并点击登录 THEN App SHALL 验证凭证并跳转至 Skill_集合 主页面
5. IF 用户提交无效的邮箱或密码 THEN App SHALL 显示明确的错误提示信息，并保留在登录界面
6. WHEN 用户点击"注册"入口 THEN App SHALL 显示注册表单，包含邮箱输入框、发送验证码按钮、验证码输入框和密码设置框
7. WHEN 用户输入邮箱并点击发送验证码 THEN App SHALL 向该邮箱发送一次性验证码
8. WHEN 用户填写有效的邮箱、验证码和密码并提交注册 THEN App SHALL 创建用户账号并自动登录
9. IF 用户提交的注册验证码无效或已过期 THEN App SHALL 显示验证码错误提示，并允许重新发送
10. WHEN 用户已登录 THEN App SHALL 在本地保存会话状态，下次启动时自动登录
11. WHEN 用户点击退出登录 THEN App SHALL 清除本地会话状态并返回登录界面
12. THE App SHALL 在登录界面展示产品亮点和品牌宣传信息，采用吸引人的视觉设计

### 需求 2：Skill 集合展示与治理

**用户故事：** 作为公司员工，我希望在主页面看到所有可用的 Skill，并能通过分类、搜索和收藏快速找到适合我工作需求的技能。

#### 验收标准

1. WHEN 用户登录成功 THEN App SHALL 在主页面展示所有可用 Skill 的卡片列表，每个 Skill 卡片包含名称、图标、分类标签和简要描述
2. WHEN Skill_集合 页面加载时 THEN App SHALL 从服务端获取最新的 Skill 列表数据
3. WHEN 用户在 Skill_集合 页面输入搜索关键词 THEN App SHALL 实时过滤并展示名称或描述匹配的 Skill
4. THE App SHALL 将 Skill 按 Skill_分类 进行分组展示，用户可按分类筛选 Skill
5. WHEN 用户点击收藏按钮 THEN App SHALL 将该 Skill 标记为收藏，并在 Skill_集合 页面顶部的"我的收藏"区域展示
6. THE App SHALL 在 Skill_集合 页面展示"最近使用"区域，按使用时间倒序显示用户最近使用过的 Skill
7. THE App SHALL 预置默认_Skill（包括但不限于：翻译助手、文案撰写、代码助手、数据分析），确保用户开箱即用
8. IF 服务端无法连接 THEN App SHALL 显示网络错误提示，并提供重试按钮

### 需求 3：Skill 选择与自然语言交互

**用户故事：** 作为公司员工，我希望选择一个 Skill 后查看使用说明，并通过自然语言描述任务，以便 AI 帮我完成工作。

#### 验收标准

1. WHEN 用户点击某个 Skill THEN App SHALL 进入该 Skill 的对话界面，显示 Skill 名称、功能说明和 Skill_使用说明
2. WHEN 用户在对话界面输入自然语言文本并发送 THEN App SHALL 将用户输入连同 Skill 上下文发送至大模型服务
3. WHEN 大模型返回响应 THEN App SHALL 在对话界面中以对话气泡形式展示响应内容
4. WHILE 大模型正在处理请求 THEN App SHALL 显示加载指示器，告知用户正在处理中
5. IF 大模型服务不可用 THEN App SHALL 显示服务不可用的错误提示，并建议用户稍后重试
6. WHEN 用户在对话界面中 THEN App SHALL 保留当前会话的对话历史，支持上下文连续对话
7. WHEN 用户点击返回按钮 THEN App SHALL 返回 Skill_集合 主页面
8. THE App SHALL 在每个 Skill 的对话界面提供"查看使用说明"入口，展示该 Skill 的详细功能描述、适用场景和输入示例

### 需求 4：用户反馈

**用户故事：** 作为公司员工，当我在 Skill_集合 中找不到需要的技能时，我希望提交反馈，以便管理员了解需求并集成新的 Skill。

#### 验收标准

1. WHEN 用户点击反馈按钮 THEN App SHALL 显示反馈表单，包含反馈内容输入框和提交按钮
2. WHEN 用户填写反馈内容并点击提交 THEN App SHALL 将反馈内容发送至服务端
3. WHEN 服务端收到反馈 THEN 反馈系统 SHALL 通过飞书 Webhook 将反馈消息发送给管理员
4. WHEN 反馈提交成功 THEN App SHALL 显示提交成功的确认提示
5. IF 反馈提交失败 THEN App SHALL 显示提交失败的错误提示，并保留用户已填写的反馈内容
6. WHEN 用户提交反馈 THEN 反馈系统 SHALL 在反馈消息中包含用户名称、提交时间和反馈内容

### 需求 5：管理员管理端

**用户故事：** 作为管理员，我希望通过管理端管理 Skill 和查看用户反馈，以便维护和扩展 App 的功能。

#### 验收标准

1. WHEN 管理员登录管理端 THEN 管理端 SHALL 显示管理仪表盘，包含 Skill 管理和反馈查看入口
2. WHEN 管理员进入 Skill 管理页面 THEN 管理端 SHALL 展示所有已集成 Skill 的列表，包含名称、状态和描述
3. WHEN 管理员使用命令行集成新 Skill THEN 管理端 SHALL 提供命令行界面，支持通过命令快速添加、配置和启用新 Skill
4. WHEN 管理员修改 Skill 状态（启用或禁用） THEN 管理端 SHALL 立即更新 Skill 状态，用户端在下次刷新时生效
5. WHEN 管理员查看反馈列表 THEN 管理端 SHALL 按时间倒序展示所有用户反馈，包含用户名称、反馈内容和提交时间
6. WHEN 管理员标记反馈为已处理 THEN 管理端 SHALL 更新反馈状态并记录处理时间

### 需求 6：跨平台支持

**用户故事：** 作为公司员工，我希望在 Windows 和 macOS 上都能使用 App，以便不受操作系统限制。

#### 验收标准

1. THE App SHALL 在 Windows 10 及以上版本和 macOS 12 及以上版本上正常运行
2. THE App SHALL 在两个平台上提供一致的用户界面和交互体验
3. THE App SHALL 使用 Java 作为主要开发语言，通过跨平台 UI 框架实现双平台支持
4. WHEN App 在不同平台启动时 THEN App SHALL 自动适配当前操作系统的窗口管理和系统托盘行为

### 需求 7：数据持久化与同步

**用户故事：** 作为公司员工，我希望我的对话历史和偏好设置能够被保存，以便下次使用时可以继续之前的工作。

#### 验收标准

1. WHEN 用户与 Skill 进行对话 THEN App SHALL 将对话历史持久化存储到本地数据库
2. WHEN 用户重新打开某个 Skill 的对话 THEN App SHALL 加载并展示之前的对话历史
3. WHEN 用户修改偏好设置 THEN App SHALL 将设置变更立即保存到本地存储
4. THE App SHALL 使用本地数据库（如 SQLite）存储用户数据，确保数据安全和完整性

### 需求 8：历史记录与成果查看

**用户故事：** 作为公司员工，我希望能够查看之前使用 Skill 产生的对话成果，以便回顾和复用之前的工作结果。

#### 验收标准

1. THE App SHALL 在侧边栏或导航中提供"历史记录"入口，展示用户所有的对话会话列表
2. WHEN 用户打开历史记录页面 THEN App SHALL 按时间倒序展示所有对话会话，每条记录包含 Skill 名称、最后一条消息摘要和时间
3. WHEN 用户点击某条历史记录 THEN App SHALL 加载并展示该会话的完整对话内容
4. WHEN 用户在历史记录中搜索关键词 THEN App SHALL 在对话内容中匹配并展示包含该关键词的会话
5. WHEN 用户删除某条历史记录 THEN App SHALL 从本地数据库中移除该会话的所有对话数据

### 需求 9：前端界面设计风格

**用户故事：** 作为公司员工，我希望 App 界面简洁现代、易于使用，以便高效完成工作。

#### 验收标准

1. THE App SHALL 采用现代简洁的设计风格，以卡片式布局展示 Skill，配合柔和的配色方案
2. THE App SHALL 支持浅色和深色两种主题模式，用户可在设置中切换
3. THE App SHALL 使用左侧导航栏布局，包含 Skill_集合、历史记录、收藏和设置等导航项
4. THE App SHALL 在对话界面采用气泡式聊天布局，用户消息靠右、AI 回复靠左，支持 Markdown 渲染
5. THE App SHALL 使用 JavaFX CSS 自定义样式，确保界面在 Windows 和 macOS 上视觉一致

### 需求 10：构建与打包

**用户故事：** 作为开发者，我希望项目有完整的构建配置，以便能够一键构建出可分发的安装包。

#### 验收标准

1. THE 项目 SHALL 使用 Maven 作为构建工具，提供完整的 pom.xml 配置文件
2. WHEN 执行构建命令 THEN 构建系统 SHALL 编译源码、运行测试并生成可执行 JAR 包
3. THE 构建系统 SHALL 通过 jpackage-maven-plugin 插件生成 Windows 安装包（.msi 或 .exe）和 macOS 安装包（.dmg）
4. THE 项目 SHALL 在 pom.xml 中声明所有依赖项（JavaFX、sqlite-jdbc、Gson、jqwik、flexmark-java 等）
5. WHEN 在全新环境中克隆项目 THEN 开发者 SHALL 能够通过 `./mvnw package` 完成完整构建

### 需求 11：部署与初始化

**用户故事：** 作为运维人员，我希望有清晰的部署文档和初始化脚本，以便快速部署后端服务和分发客户端。

#### 验收标准

1. THE 项目 SHALL 提供 README.md 文件，包含项目简介、技术栈、目录结构、构建步骤和部署说明
2. THE 项目 SHALL 提供初始化脚本（init.sh / init.bat），用于创建本地数据库、初始化默认配置和预置默认_Skill 数据
3. THE 项目 SHALL 提供应用配置文件（application.properties 或 config.json），包含大模型 API 地址、飞书 Webhook URL 等可配置项
4. WHEN 初始化脚本执行完成 THEN App SHALL 能够正常启动并展示预置的默认_Skill
5. THE 项目 SHALL 提供 Docker 配置文件（Dockerfile），用于容器化部署后端 API 服务

### 需求 12：项目结构与源码组织

**用户故事：** 作为开发者，我希望项目有清晰的目录结构和代码组织，以便高效地开发和维护。

#### 验收标准

1. THE 项目 SHALL 按照标准 Java 项目结构组织源码（src/main/java、src/main/resources、src/test/java）
2. THE 项目 SHALL 按功能模块划分包结构（auth、skill、chat、feedback、admin、common）
3. THE 项目 SHALL 将 FXML 布局文件和 CSS 样式文件放置在 src/main/resources 目录下
4. THE 项目 SHALL 将配置文件模板和初始化脚本放置在项目根目录的 scripts/ 目录下
5. THE 项目 SHALL 在 .gitignore 中排除构建产物、IDE 配置和本地数据库文件

### 需求 13：Skill 知识库绑定与 RAG 检索

**用户故事：** 作为管理员，我希望为每个 Skill 关联知识库文件，系统通过 RAG 检索增强生成，以便大模型在回答时参考相关知识片段，提升回答的准确性和专业性。

#### 验收标准

1. WHEN 管理员为 Skill 绑定知识库文件 THEN 管理端 SHALL 将文件与该 Skill 建立关联关系，并存储文件元数据（文件名、大小、类型、上传时间）
2. THE 管理端 SHALL 支持 PDF、Word、Markdown 和 TXT 格式的知识库文件上传
3. WHEN 知识库文件上传完成 THEN 管理端 SHALL 对文件内容进行文本提取、分块处理和向量化存储
4. WHEN 用户与绑定了知识库的 Skill 进行对话 THEN App SHALL 使用 RAG 从知识库中检索与用户输入语义相关的文档片段，将检索结果作为上下文注入大模型请求
5. WHEN 管理员解除 Skill 与知识库文件的绑定 THEN 管理端 SHALL 移除关联关系和对应的向量索引，后续对话不再引用该文件内容
6. THE 管理端 SHALL 支持一个 Skill 绑定多个知识库文件，RAG 检索时从所有已绑定文件的向量索引中检索
7. WHEN 管理员更新已绑定的知识库文件 THEN 管理端 SHALL 重新提取文本、重新分块和重新向量化存储

### 需求 14：管理端知识库管理

**用户故事：** 作为管理员，我希望在管理端上传、更新和删除知识库文件，以便维护 Skill 的知识来源。

#### 验收标准

1. WHEN 管理员进入知识库管理页面 THEN 管理端 SHALL 展示所有已上传的知识库文件列表，包含文件名、文件类型、文件大小、上传时间和关联的 Skill 名称
2. WHEN 管理员上传新的知识库文件 THEN 管理端 SHALL 验证文件格式（仅允许 PDF、Markdown、TXT），存储文件并提取文本内容
3. WHEN 管理员更新已有的知识库文件 THEN 管理端 SHALL 替换原文件内容，重新提取文本，并保留与 Skill 的关联关系
4. WHEN 管理员删除知识库文件 THEN 管理端 SHALL 移除文件及其提取的文本内容，同时解除与所有 Skill 的关联
5. IF 上传的文件格式不在支持范围内 THEN 管理端 SHALL 拒绝上传并显示明确的格式错误提示
6. IF 文件文本提取失败 THEN 管理端 SHALL 显示提取失败的错误提示，并允许管理员重试或手动输入文本内容

### 需求 15：Skill 模板化创建（含知识库 Skill 创建向导）

**用户故事：** 作为管理员，我希望通过可视化创建向导快速创建 Skill（包括知识库类型 Skill），以便无需命令行操作即可便捷地将公司内部知识库添加为 Skill。

#### 验收标准

1. WHEN 管理员进入 Skill 创建页面 THEN 管理端 SHALL 显示 Skill_创建向导，第一步为选择 Skill 类型（通用型_Skill 或 知识型_Skill）
2. WHEN 管理员选择创建通用型_Skill THEN 管理端 SHALL 显示创建表单，包含名称、描述、分类选择、图标上传和 Prompt_模板 编辑区域
3. WHEN 管理员选择创建知识型_Skill THEN 管理端 SHALL 显示知识库 Skill 创建向导，分步流程为：上传知识库文档或输入知识内容 → 配置 Skill 名称、描述、分类、图标 → 配置 Prompt_模板 → 预览测试 → 发布
4. WHEN 管理员在知识库 Skill 创建向导中上传文档 THEN 管理端 SHALL 支持批量上传多个知识库文件（PDF、Markdown、TXT），并实时显示文件处理进度
5. WHEN 管理员在知识库 Skill 创建向导中选择手动输入 THEN 管理端 SHALL 提供富文本编辑器，支持管理员直接输入知识内容（如 FAQ、流程手册等）
6. WHEN 管理员填写完表单并点击发布 THEN 管理端 SHALL 验证必填字段（名称、描述、分类），创建 Skill 并设置为启用状态
7. IF 管理员提交的表单缺少必填字段 THEN 管理端 SHALL 高亮缺失字段并显示验证错误提示
8. WHEN 管理员在创建表单中上传知识库文件 THEN 管理端 SHALL 自动将文件与新创建的 Skill 绑定
9. WHEN 管理员编辑 Prompt_模板 THEN 管理端 SHALL 提供模板预览功能，展示模板与示例输入组合后的效果
10. WHEN Skill 创建成功 THEN 管理端 SHALL 显示创建成功提示，新 Skill 立即出现在 Skill 管理列表中
11. THE 管理端 SHALL 支持管理员选择新创建的 Skill 为通用_Skill 或内部_Skill
12. WHEN 管理员更新已发布的知识型_Skill 的知识库内容 THEN 管理端 SHALL 重新处理知识库内容，用户端在下次对话时自动使用更新后的知识内容

### 需求 16：后端数据存储

**用户故事：** 作为开发者，我希望后端使用 MySQL 作为关系型数据库、Redis 作为缓存，以便支撑多用户并发访问和高性能数据查询。

#### 验收标准

1. THE 后端服务 SHALL 使用 MySQL 作为关系型数据库，存储用户账号、Skill 配置、知识库文件元数据、反馈记录等业务数据
2. THE 后端服务 SHALL 使用 Redis 作为缓存层，缓存 Skill 列表、用户会话状态和热点数据
3. THE App 客户端 SHALL 继续使用 SQLite 作为本地数据库，存储对话历史、用户偏好设置和离线数据
4. WHEN 后端 Skill 配置数据发生变更 THEN 后端服务 SHALL 清除 Redis 中对应的 Skill 缓存，确保客户端获取最新数据
5. WHEN 用户登录成功 THEN 后端服务 SHALL 将会话信息存储到 Redis，设置合理的过期时间
6. THE 项目 SHALL 在配置文件中提供 MySQL 连接参数（地址、端口、数据库名、用户名、密码）和 Redis 连接参数（地址、端口、密码）

### 需求 17：内部 Skill 标记

**用户故事：** 作为公司员工，我希望能区分通用 Skill 和内部 Skill，以便快速识别公司专属的技能。

#### 验收标准

1. THE App SHALL 在 Skill 卡片上为内部_Skill 显示公司标识徽章，与通用_Skill 进行视觉区分
2. WHEN 用户在 Skill_集合 页面筛选时 THEN App SHALL 支持按"全部"、"通用"和"内部"进行筛选
3. THE App SHALL 在 Skill 详情和对话界面中显示 Skill 的类型标识（通用或内部）
4. WHEN 管理员创建或编辑 Skill 时 THEN 管理端 SHALL 提供 Skill 类型选择（通用_Skill 或内部_Skill），默认为通用_Skill
5. THE App SHALL 在"我的收藏"和"最近使用"区域中同样显示内部_Skill 的公司标识徽章
