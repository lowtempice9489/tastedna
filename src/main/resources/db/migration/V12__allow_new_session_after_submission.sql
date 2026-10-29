-- 제출된 테스트는 기록으로 보존하되 새 테스트 생성을 차단하지 않는다.
-- 회원당 진행 중(IN_PROGRESS) 세션만 하나로 제한한다.

ALTER TABLE td_test_session
DROP INDEX uq_td_test_session_active_member;

ALTER TABLE td_test_session
DROP COLUMN active_member_id;

ALTER TABLE td_test_session
    ADD COLUMN active_member_id VARCHAR(128)
        GENERATED ALWAYS AS (
            CASE
                WHEN status = 'IN_PROGRESS'
                    THEN mall_member_id
                ELSE NULL
                END
            ) STORED;

ALTER TABLE td_test_session
    ADD CONSTRAINT uq_td_test_session_active_member
        UNIQUE (active_member_id);