-- 제출 이력은 전환하지 않는다. 저장 슬롯과 현재 설정은 서로 독립적인 보호 의사다.
CREATE TABLE td_member_result_retention (
    session_id CHAR(36) NOT NULL,
    mall_member_id VARCHAR(128) NOT NULL,
    submission_revision BIGINT NOT NULL,
    rule_version VARCHAR(255) NOT NULL,
    result_snapshot JSON NOT NULL,
    captured_at DATETIME(6) NOT NULL,
    saved_slot INT NULL,
    saved_at DATETIME(6) NULL,
    current_marker INT NULL,
    current_since DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (session_id),
    CONSTRAINT fk_result_retention_owner FOREIGN KEY (session_id, mall_member_id)
        REFERENCES td_test_session (session_id, mall_member_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uq_result_retention_slot UNIQUE (mall_member_id, saved_slot),
    CONSTRAINT uq_result_retention_current UNIQUE (mall_member_id, current_marker),
    CONSTRAINT chk_result_retention_slot CHECK (
        (saved_slot IS NULL AND saved_at IS NULL) OR
        (saved_slot IS NOT NULL AND saved_slot BETWEEN 1 AND 5 AND saved_at IS NOT NULL)),
    CONSTRAINT chk_result_retention_current CHECK (
        (current_marker IS NULL AND current_since IS NULL) OR
        (current_marker IS NOT NULL AND current_marker = 1 AND current_since IS NOT NULL)),
    CONSTRAINT chk_result_retention_revision CHECK (submission_revision >= 0),
    CONSTRAINT chk_result_retention_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
