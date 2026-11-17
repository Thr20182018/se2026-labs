-- 新增场次单独提交审核，版本独立于所属活动的内容修改审核。
ALTER TABLE sessions ADD COLUMN review_version BIGINT NOT NULL DEFAULT 0;
