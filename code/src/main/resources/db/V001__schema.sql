-- 校园工作坊业务表。时间列保存北京时间（UTC+8）的墙上时间。
-- 在空数据库执行一次，后续结构变更使用新的编号脚本。

CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account VARCHAR(64) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  role VARCHAR(16) NOT NULL,
  CONSTRAINT uk_users_account UNIQUE (account),
  CONSTRAINT ck_users_role CHECK (
    role IN ('PARTICIPANT', 'ORGANIZER', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE activities (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(200) NOT NULL,
  description TEXT NOT NULL,
  organizer_id BIGINT NOT NULL,
  publication_status VARCHAR(16) NOT NULL,
  pending_name VARCHAR(200) NULL,
  pending_description TEXT NULL,
  revision_pending TINYINT(1) NOT NULL DEFAULT 0,
  CONSTRAINT fk_activities_organizer
    FOREIGN KEY (organizer_id) REFERENCES users (id),
  CONSTRAINT ck_activities_status CHECK (
    publication_status IN (
      'DRAFT', 'PENDING', 'REJECTED', 'PUBLISHED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sessions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  activity_id BIGINT NOT NULL,
  start_time DATETIME(0) NOT NULL,
  end_time DATETIME(0) NOT NULL,
  registration_deadline DATETIME(0) NOT NULL,
  location VARCHAR(200) NOT NULL,
  capacity INT NOT NULL,
  publication_status VARCHAR(16) NOT NULL,
  CONSTRAINT fk_sessions_activity
    FOREIGN KEY (activity_id) REFERENCES activities (id),
  CONSTRAINT ck_sessions_time CHECK (
    registration_deadline <= start_time AND start_time < end_time),
  CONSTRAINT ck_sessions_capacity CHECK (capacity >= 0),
  CONSTRAINT ck_sessions_status CHECK (
    publication_status IN (
      'DRAFT', 'PENDING', 'REJECTED', 'PUBLISHED', 'CANCELLED')),
  INDEX idx_sessions_start (start_time),
  INDEX idx_sessions_activity_status (activity_id, publication_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 非取消记录共用一个生成列。已取消写成 NULL，因此多条取消历史可以共存。
CREATE TABLE registrations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  participant_id BIGINT NOT NULL,
  session_id BIGINT NOT NULL,
  registered_at DATETIME(0) NOT NULL,
  status VARCHAR(32) NOT NULL,
  checked_in_at DATETIME(0) NULL,
  waitlist_order INT NULL,
  active_pair VARCHAR(64)
    GENERATED ALWAYS AS (
      IF(status = 'CANCELLED', NULL, CONCAT(participant_id, '#', session_id))
    ) STORED,
  CONSTRAINT fk_registrations_participant
    FOREIGN KEY (participant_id) REFERENCES users (id),
  CONSTRAINT fk_registrations_session
    FOREIGN KEY (session_id) REFERENCES sessions (id),
  CONSTRAINT uk_registrations_active_pair UNIQUE (active_pair),
  CONSTRAINT ck_registrations_status CHECK (
    status IN (
      'WAITLIST', 'REGISTERED', 'CANCELLED',
      'CHECKED_IN', 'ABSENT', 'WAITLIST_EXPIRED')),
  INDEX idx_registrations_session_status (session_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 目标编号故意不加外键：失败日志要能记下不存在的活动或报名。
CREATE TABLE operation_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  operated_at DATETIME(0) NOT NULL,
  actor_type VARCHAR(16) NOT NULL,
  actor_id BIGINT NULL,
  activity_id BIGINT NULL,
  session_id BIGINT NULL,
  registration_id BIGINT NULL,
  action VARCHAR(64) NOT NULL,
  result VARCHAR(16) NOT NULL,
  failure_reason VARCHAR(500) NULL,
  before_value TEXT NULL,
  after_value TEXT NULL,
  CONSTRAINT fk_logs_actor FOREIGN KEY (actor_id) REFERENCES users (id),
  CONSTRAINT ck_logs_actor CHECK (
    (actor_type = 'SYSTEM' AND actor_id IS NULL)
    OR (actor_type = 'USER' AND actor_id IS NOT NULL)),
  CONSTRAINT ck_logs_result CHECK (result IN ('SUCCESS', 'FAILURE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
