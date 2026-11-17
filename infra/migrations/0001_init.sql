-- 小念 P0 初始 schema
-- 约定：主键由应用层生成（crypto.randomUUID），不依赖 pgcrypto 扩展

CREATE TABLE IF NOT EXISTS users (
  id             uuid        PRIMARY KEY,
  phone_or_email text        NOT NULL UNIQUE,
  locale         text        NOT NULL DEFAULT 'zh-CN',
  timezone       text        NOT NULL,
  plan           text        NOT NULL DEFAULT 'free',
  created_at     timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS device_sessions (
  id          uuid        PRIMARY KEY,
  user_id     uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_name text        NOT NULL,
  platform    text        NOT NULL,
  public_key  text        NOT NULL,
  status      text        NOT NULL DEFAULT 'active',
  last_seen_at timestamptz,
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS device_sessions_user_idx ON device_sessions (user_id);

CREATE TABLE IF NOT EXISTS conversations (
  id               uuid        PRIMARY KEY,
  user_id          uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title            text,
  title_source     text        NOT NULL DEFAULT 'auto',
  status           text        NOT NULL DEFAULT 'active',
  source_import_id text,
  last_message_at  timestamptz,
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS conversations_user_idx ON conversations (user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS messages (
  id              uuid        PRIMARY KEY,
  conversation_id uuid        NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  parent_id       uuid        REFERENCES messages(id) ON DELETE SET NULL,
  seq             bigint      NOT NULL,
  role            text        NOT NULL,
  blocks          jsonb       NOT NULL DEFAULT '[]'::jsonb,
  provider        text,
  model           text,
  status          text        NOT NULL,
  usage           jsonb,
  error_code      text,
  can_continue    boolean     NOT NULL DEFAULT false,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  UNIQUE (conversation_id, seq)
);
CREATE INDEX IF NOT EXISTS messages_conv_idx ON messages (conversation_id, seq);

CREATE TABLE IF NOT EXISTS conversation_files (
  id               uuid        PRIMARY KEY,
  user_id          uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  conversation_id  uuid        REFERENCES conversations(id) ON DELETE SET NULL,
  provider         text        NOT NULL,
  provider_file_id text,
  name             text        NOT NULL,
  mime             text        NOT NULL,
  size             bigint      NOT NULL,
  purpose          text,
  state            text        NOT NULL,
  sha256           text,
  expires_at       timestamptz,
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS conversation_files_user_idx ON conversation_files (user_id);
CREATE INDEX IF NOT EXISTS conversation_files_sha_idx ON conversation_files (sha256);

CREATE TABLE IF NOT EXISTS usage_events (
  id              uuid        PRIMARY KEY,
  user_id         uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  conversation_id uuid,
  message_id      uuid,
  capability      text        NOT NULL,
  provider        text        NOT NULL,
  model           text,
  usage           jsonb       NOT NULL,
  trace_id        text        NOT NULL,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS usage_events_user_idx ON usage_events (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS audit_events (
  id         uuid        PRIMARY KEY,
  trace_id   text        NOT NULL,
  user_id    uuid,
  actor      text        NOT NULL,
  action     text        NOT NULL,
  target     text,
  risk_level text        NOT NULL DEFAULT 'read',
  result     text        NOT NULL,
  detail     jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS audit_events_trace_idx ON audit_events (trace_id);

-- 全局单调变更日志：断线增量同步的唯一游标（客户端只信 seq 的单调性）
CREATE TABLE IF NOT EXISTS change_log (
  seq        bigserial   PRIMARY KEY,
  user_id    uuid        NOT NULL,
  entity     text        NOT NULL,
  entity_id  uuid        NOT NULL,
  op         text        NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS change_log_user_idx ON change_log (user_id, seq);

-- 客户端重发去重：request_hash 不同则视为冲突（409）
CREATE TABLE IF NOT EXISTS idempotency_keys (
  key          text        NOT NULL,
  user_id      uuid        NOT NULL,
  route        text        NOT NULL,
  request_hash text        NOT NULL,
  status       integer     NOT NULL,
  response     jsonb,
  created_at   timestamptz NOT NULL DEFAULT now(),
  expires_at   timestamptz NOT NULL,
  PRIMARY KEY (key, user_id)
);
