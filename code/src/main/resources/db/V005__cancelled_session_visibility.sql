-- 保留取消前的发布状态，公开列表仅展示曾经发布的取消场次。
ALTER TABLE sessions ADD COLUMN published_before_cancel BOOLEAN NOT NULL DEFAULT FALSE;

-- T4/T5 之前的已取消演示场次来自已发布活动。
UPDATE sessions SET published_before_cancel = TRUE WHERE publication_status = 'CANCELLED';
