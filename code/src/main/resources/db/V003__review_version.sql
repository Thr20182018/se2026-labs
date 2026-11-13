-- 每次提交审核递增版本，旧页面只能处理它实际展示的那一轮申请。
ALTER TABLE activities ADD COLUMN review_version BIGINT NOT NULL DEFAULT 0;
