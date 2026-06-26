CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS sys_user (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_session (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
  device_id TEXT NOT NULL,
  user_agent TEXT NOT NULL DEFAULT '',
  token_hash TEXT NOT NULL,
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS user_session_user_idx ON user_session(user_id);
CREATE INDEX IF NOT EXISTS user_session_token_idx ON user_session(token_hash);

CREATE TABLE IF NOT EXISTS user_skill (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
  skill_tag TEXT NOT NULL,
  self_level INTEGER NOT NULL CHECK (self_level BETWEEN 1 AND 5),
  is_verified BOOLEAN NOT NULL DEFAULT false,
  ai_score INTEGER,
  ai_comment TEXT,
  dynamic_score NUMERIC(10,2) NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS user_skill_user_tag_unique
  ON user_skill(user_id, lower(skill_tag));

CREATE TABLE IF NOT EXISTS oa_project (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  requirement_text TEXT NOT NULL DEFAULT '',
  owner_id UUID NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
  project_progress NUMERIC(5,2) NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS oa_project_member (
  project_id UUID NOT NULL REFERENCES oa_project(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
  member_role TEXT NOT NULL CHECK (member_role IN ('leader', 'member')),
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (project_id, user_id)
);

CREATE TABLE IF NOT EXISTS oa_task (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES oa_project(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  category TEXT NOT NULL DEFAULT '通用',
  status TEXT NOT NULL DEFAULT 'todo' CHECK (status IN ('todo', 'in_progress', 'review', 'done')),
  assignee_id UUID REFERENCES sys_user(id) ON DELETE SET NULL,
  features_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  estimated_days NUMERIC(8,2) NOT NULL DEFAULT 1,
  residual_progress NUMERIC(5,2) NOT NULL DEFAULT 0,
  ai_review_status TEXT NOT NULL DEFAULT 'pending' CHECK (ai_review_status IN ('pending', 'passed', 'rejected')),
  ai_review_comment TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS oa_task_project_idx ON oa_task(project_id);
CREATE INDEX IF NOT EXISTS oa_task_assignee_idx ON oa_task(assignee_id);

CREATE TABLE IF NOT EXISTS oa_task_event (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  task_id UUID NOT NULL REFERENCES oa_task(id) ON DELETE CASCADE,
  project_id UUID NOT NULL REFERENCES oa_project(id) ON DELETE CASCADE,
  actor_id UUID NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
  event_type TEXT NOT NULL,
  from_value TEXT,
  to_value TEXT,
  reason TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS oa_task_event_task_idx ON oa_task_event(task_id, created_at DESC);
CREATE INDEX IF NOT EXISTS oa_task_event_project_idx ON oa_task_event(project_id, created_at DESC);

CREATE TABLE IF NOT EXISTS oa_task_submission (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  task_id UUID NOT NULL REFERENCES oa_task(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
  repo_url TEXT NOT NULL,
  submit_type TEXT NOT NULL CHECK (submit_type IN ('GITHUB', 'GITEE')),
  ai_passed BOOLEAN NOT NULL DEFAULT false,
  ai_score INTEGER NOT NULL DEFAULT 0,
  ai_comment TEXT NOT NULL DEFAULT '',
  residual_progress NUMERIC(5,2) NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS oa_submission_task_idx ON oa_task_submission(task_id);
