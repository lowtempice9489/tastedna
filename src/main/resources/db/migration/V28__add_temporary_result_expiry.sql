-- 과거 제출은 NULL로 남긴다. 신규 제출 경로만 5일 적용 경계를 기록한다.
ALTER TABLE td_test_session
    ADD COLUMN temporary_result_expires_at DATETIME(6) NULL,
    ADD INDEX idx_session_temporary_result_expiry (mall_member_id, status, temporary_result_expires_at);
