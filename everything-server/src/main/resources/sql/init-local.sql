-- Yeahmobi Everything - Local SQLite Database Initialization
-- ============================================================
-- Client-side SQLite tables for chat history, user preferences, and offline data.

-- 本地会话缓存表
CREATE TABLE IF NOT EXISTS local_session (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    token TEXT NOT NULL,
    user_id TEXT NOT NULL,
    username TEXT NOT NULL,
    email TEXT,
    login_type TEXT NOT NULL CHECK(login_type IN ('email', 'feishu')),
    expires_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL DEFAULT 0,
    is_admin INTEGER NOT NULL DEFAULT 0
);

-- 对话会话表
CREATE TABLE IF NOT EXISTS chat_session (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    skill_id TEXT NOT NULL,
    skill_name TEXT NOT NULL,
    last_message TEXT,
    last_timestamp INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_session_user ON chat_session(user_id);

-- 对话历史表
CREATE TABLE IF NOT EXISTS chat_message (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL,
    skill_id TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('user', 'assistant')),
    content TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_chat_session ON chat_message(session_id);
CREATE INDEX IF NOT EXISTS idx_chat_skill ON chat_message(skill_id);
CREATE INDEX IF NOT EXISTS idx_chat_timestamp ON chat_message(timestamp);

-- 用户收藏表（本地缓存）
CREATE TABLE IF NOT EXISTS favorite (
    user_id TEXT NOT NULL,
    skill_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (user_id, skill_id)
);

-- Skill 使用记录表（本地缓存）
CREATE TABLE IF NOT EXISTS skill_usage (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    skill_id TEXT NOT NULL,
    used_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_usage_user ON skill_usage(user_id, used_at);

-- 用户设置表
CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- 个人 Skill 表
CREATE TABLE IF NOT EXISTS personal_skill (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    category TEXT NOT NULL,
    prompt_template TEXT NOT NULL,
    status TEXT NOT NULL CHECK(status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED')),
    reviewer_note TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_personal_skill_user ON personal_skill(user_id);
CREATE INDEX IF NOT EXISTS idx_personal_skill_status ON personal_skill(status);
