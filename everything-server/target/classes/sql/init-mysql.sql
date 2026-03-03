-- Yeahmobi Everything - MySQL Database Initialization
-- ============================================================
-- Backend MySQL tables for user accounts, skill configuration,
-- knowledge base metadata, and feedback records.

-- 用户账号表
CREATE TABLE IF NOT EXISTS user (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) UNIQUE,
    password_hash VARCHAR(255),
    username VARCHAR(100) NOT NULL,
    feishu_user_id VARCHAR(100) UNIQUE,
    login_type ENUM('email', 'feishu') NOT NULL,
    is_admin BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_email (email),
    INDEX idx_user_feishu (feishu_user_id),
    INDEX idx_user_admin (is_admin)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Skill 配置表
CREATE TABLE IF NOT EXISTS skill (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    icon VARCHAR(255),
    category VARCHAR(50) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    usage_guide TEXT,
    examples_json TEXT,
    i18n_json LONGTEXT,
    source VARCHAR(50) DEFAULT 'admin',
    source_lang VARCHAR(20),
    quality_tier ENUM('basic', 'verified') DEFAULT 'basic',
    tool_ids_json TEXT,
    tool_groups_json TEXT,
    context_policy VARCHAR(50) DEFAULT 'default',
    skill_type ENUM('GENERAL', 'INTERNAL') DEFAULT 'GENERAL',
    skill_kind ENUM('PROMPT_ONLY', 'KNOWLEDGE_RAG') DEFAULT 'PROMPT_ONLY',
    prompt_template TEXT,
    execution_mode ENUM('SINGLE', 'MULTI') DEFAULT 'SINGLE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_skill_category (category),
    INDEX idx_skill_type (skill_type),
    INDEX idx_skill_kind (skill_kind),
    INDEX idx_skill_quality_tier (quality_tier),
    INDEX idx_skill_context_policy (context_policy)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed default Skills (for first-time usage)
INSERT IGNORE INTO skill (
    id, name, description, icon, category, enabled, usage_guide, examples_json, source, source_lang, quality_tier,
    skill_type, skill_kind, prompt_template, execution_mode
) VALUES
(
    'b2f2e2d7-9c5e-4d4c-9a2b-1b7d9b001001',
    '高效会议纪要',
    '将会议内容整理为结构化纪要（议题、结论、待办）。',
    NULL,
    '效率',
    TRUE,
    '输入会议记录或录音转写，系统会输出纪要、结论与待办事项。',
    '["请将以下会议记录整理为纪要：\\n<粘贴会议记录>","把这段录音转写整理成待办清单：\\n<粘贴文本>"]',
    'seed',
    'zh',
    'verified',
    'GENERAL',
    'PROMPT_ONLY',
    '请将以下会议记录整理成结构化纪要：\n1) 参会人员\n2) 讨论议题\n3) 关键结论\n4) 待办事项（负责人 + 截止时间）\n\n会议记录：\n{{input}}',
    'SINGLE'
),
(
    'b2f2e2d7-9c5e-4d4c-9a2b-1b7d9b001002',
    '专业写作助手',
    '快速生成高质量邮件、公告、报告与文案。',
    NULL,
    '通用',
    TRUE,
    '输入需求（受众、语气、主题），即可生成高质量文本。',
    '["写一封商务邮件：\\n- 收件人：\\n- 目的：\\n- 语气：\\n- 关键点：","写一段公告：\\n- 面向：\\n- 背景：\\n- 要求："]',
    'seed',
    'zh',
    'verified',
    'GENERAL',
    'PROMPT_ONLY',
    '你是专业写作助手。请根据以下要求生成内容：\n- 受众：{{audience}}\n- 语气：{{tone}}\n- 主题：{{topic}}\n- 关键点：{{input}}\n\n请输出结构清晰、语言专业的文本。',
    'SINGLE'
),
(
    'b2f2e2d7-9c5e-4d4c-9a2b-1b7d9b001003',
    '产品需求助手',
    '将想法转为完整 PRD（背景、目标、方案、验收）。',
    NULL,
    '产品',
    TRUE,
    '输入业务目标与功能想法，生成标准 PRD。',
    '["把这个想法写成 PRD：\\n- 背景：\\n- 目标：\\n- 用户：\\n- 功能：\\n- 约束：","把这条需求拆成验收标准：\\n<粘贴需求>"]',
    'seed',
    'zh',
    'verified',
    'GENERAL',
    'PROMPT_ONLY',
    '请将以下需求整理成 PRD：\n1) 背景与目标\n2) 用户画像\n3) 核心流程\n4) 需求列表（P0/P1/P2）\n5) 埋点与验收标准\n\n需求描述：\n{{input}}',
    'SINGLE'
),
(
    'b2f2e2d7-9c5e-4d4c-9a2b-1b7d9b001004',
    '数据分析解读',
    '将数据表转为结论和建议。',
    NULL,
    '数据',
    TRUE,
    '输入数据或指标变化，输出分析结论与建议。',
    '["请分析这份数据并给出结论与建议：\\n<粘贴表格>","解释这个指标变化可能原因，并给下一步动作：\\n<描述变化>"]',
    'seed',
    'zh',
    'verified',
    'GENERAL',
    'PROMPT_ONLY',
    '请对以下数据进行分析：\n1) 关键结论\n2) 异常点解释\n3) 可执行建议\n\n数据：\n{{input}}',
    'SINGLE'
),
(
    'b2f2e2d7-9c5e-4d4c-9a2b-1b7d9b001005',
    '代码审查助手',
    '快速发现问题并给出改进建议。',
    NULL,
    '研发',
    TRUE,
    '粘贴代码或 PR 变更，输出问题与改进建议。',
    '["请审查这段代码：\\n<粘贴代码>","请审查这个 PR 变更点：\\n<粘贴 diff>"]',
    'seed',
    'zh',
    'verified',
    'GENERAL',
    'PROMPT_ONLY',
    '请审查以下代码：\n1) 可能的 Bug\n2) 性能风险\n3) 可读性问题\n4) 可执行优化建议\n\n代码：\n{{input}}',
    'SINGLE'
);

-- Seed: 本机命令与定时任务助手（CLI + 调度）
INSERT IGNORE INTO skill (
    id, name, description, icon, category, enabled, usage_guide, examples_json, source, source_lang, quality_tier,
    tool_ids_json, tool_groups_json, context_policy,
    skill_type, skill_kind, prompt_template, execution_mode
) VALUES (
    'b2f2e2d7-9c5e-4d4c-9a2b-1b7d9b001008',
    '本机命令与定时任务助手',
    '根据当前操作系统执行本机命令，并支持创建/暂停/删除定时任务。',
    NULL,
    '自动化',
    TRUE,
    '输入目标动作，系统会先预览命令和风险，确认后执行；也可创建定时任务。',
    '["每30分钟清理一次临时目录","帮我查看当前目录并统计日志文件数量"]',
    'seed',
    'zh',
    'verified',
    '["os-cli","os-scheduler"]',
    '["machine-ops","os-cli","os-scheduler"]',
    'standard',
    'GENERAL',
    'PROMPT_ONLY',
    '你是本机命令与定时任务助手。规则：\n1) 先识别操作系统并执行工具，不要写教程。\n2) 默认先 dry-run 预览风险，再等待用户确认执行。\n3) 定时任务直接创建并返回结果，支持暂停/恢复/删除/立即执行。\n4) 禁止输出“步骤1/步骤2”，禁止要求用户输入 userConfirmed=true 等技术参数。\n5) 面向非技术用户，仅输出：我将帮你做什么、请确认、结果与下一步。\n\n用户输入：\n{{input}}',
    'SINGLE'
);

-- Seed: 个人工作跟进秘书（待办 + 提醒 + 复盘）
INSERT IGNORE INTO skill (
    id, name, description, icon, category, enabled, usage_guide, examples_json, source, source_lang, quality_tier,
    tool_ids_json, tool_groups_json, context_policy,
    skill_type, skill_kind, prompt_template, execution_mode
) VALUES (
    'b2f2e2d7-9c5e-4d4c-9a2b-1b7d9b001007',
    '个人工作跟进秘书',
    '把自然语言目标转成待办、提醒与复盘清单，持续推进个人工作闭环。',
    NULL,
    '效率',
    TRUE,
    '输入你的工作目标和时间要求，系统会生成待办清单、提醒计划与复盘点。',
    '["我需要推进合同审批，明天下午3点前提醒我","帮我把本周招聘推进拆成待办并给复盘问题"]',
    'seed',
    'zh',
    'verified',
    '["work-followup"]',
    '["work-followup","personal-assistant"]',
    'standard',
    'GENERAL',
    'PROMPT_ONLY',
    '你是个人工作跟进秘书。请将用户输入转成可执行闭环：\n1) 对执行型请求先调用工具创建任务（createTodo），再输出结果。\n2) 待办清单（优先级 + 截止时间）并附任务回执（ID/状态）。\n3) 提醒计划（提醒时间 + 提醒内容）。\n4) 复盘点（完成标准 + 复盘问题）。\n5) 若时间信息不完整，先给默认时间并提示可修改。\n6) 禁止让用户自行去设置提醒或手动创建任务。\n\n用户输入：\n{{input}}',
    'SINGLE'
);

-- Seed: 信息检索秘书（公开网页检索 + 引用证据）
INSERT IGNORE INTO skill (
    id, name, description, icon, category, enabled, usage_guide, examples_json, source, source_lang, quality_tier,
    tool_ids_json, tool_groups_json, context_policy,
    skill_type, skill_kind, prompt_template, execution_mode
) VALUES (
    'b2f2e2d7-9c5e-4d4c-9a2b-1b7d9b001006',
    '信息检索秘书',
    '按站点限定检索公开网页，输出带引用结论与下一步建议。',
    NULL,
    '检索',
    TRUE,
    '输入问题（可指定站点如 zhihu.com），系统会检索公开信息并给出引用来源。',
    '["在知乎查一下 Java Stream 常见陷阱并给来源","检索官网文档：某产品 API 限流规则"]',
    'seed',
    'zh',
    'verified',
    '["web-research"]',
    '["web-search","information-retrieval"]',
    'standard',
    'GENERAL',
    'PROMPT_ONLY',
    '你是信息检索秘书。必须先检索后回答。\n回答时请按以下结构：\n1) 结论\n2) 证据要点（至少2条，附URL）\n3) 下一步建议\n若证据不足，请明确说明“证据不足”，不要编造。\n\n用户问题：\n{{input}}',
    'SINGLE'
);

-- 知识库文件表
CREATE TABLE IF NOT EXISTS knowledge_file (
    id VARCHAR(36) PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(10) NOT NULL,
    file_size BIGINT NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    source_type ENUM('upload', 'manual') DEFAULT 'upload',
    extracted_text LONGTEXT,
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Skill 与知识库文件关联表
CREATE TABLE IF NOT EXISTS skill_knowledge_binding (
    skill_id VARCHAR(36) NOT NULL,
    knowledge_file_id VARCHAR(36) NOT NULL,
    bound_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (skill_id, knowledge_file_id),
    INDEX idx_binding_skill (skill_id),
    INDEX idx_binding_file (knowledge_file_id),
    FOREIGN KEY (skill_id) REFERENCES skill(id) ON DELETE CASCADE,
    FOREIGN KEY (knowledge_file_id) REFERENCES knowledge_file(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户反馈表
CREATE TABLE IF NOT EXISTS feedback (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    username VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    status ENUM('pending', 'processed') DEFAULT 'pending',
    processed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_feedback_user (user_id),
    INDEX idx_feedback_status (status),
    INDEX idx_feedback_time (created_at),
    FOREIGN KEY (user_id) REFERENCES user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Skill ZIP 包安装记录（版本治理 + 幂等安装）
CREATE TABLE IF NOT EXISTS skill_package (
    id VARCHAR(36) PRIMARY KEY,
    skill_name VARCHAR(100) NOT NULL,
    skill_version VARCHAR(64) NOT NULL,
    artifact_sha256 CHAR(64) NOT NULL,
    source_url VARCHAR(1024),
    source_type ENUM('upload', 'download') DEFAULT 'upload',
    status ENUM('INSTALLED', 'FAILED') DEFAULT 'INSTALLED',
    active BOOLEAN DEFAULT TRUE,
    install_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_skill_name_version (skill_name, skill_version),
    INDEX idx_skill_package_name (skill_name),
    INDEX idx_skill_package_sha (artifact_sha256),
    INDEX idx_skill_package_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Skill ZIP 安装审计日志
CREATE TABLE IF NOT EXISTS skill_package_audit (
    id VARCHAR(36) PRIMARY KEY,
    package_id VARCHAR(36),
    skill_name VARCHAR(100),
    skill_version VARCHAR(64),
    artifact_sha256 CHAR(64),
    action VARCHAR(64) NOT NULL,
    actor VARCHAR(100),
    detail TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_skill_package_audit_name (skill_name),
    INDEX idx_skill_package_audit_action (action),
    INDEX idx_skill_package_audit_time (created_at),
    CONSTRAINT fk_skill_package_audit_package
        FOREIGN KEY (package_id) REFERENCES skill_package(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
