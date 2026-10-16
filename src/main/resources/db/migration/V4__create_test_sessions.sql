
-- TasteDNA V4
-- Member test sessions and answers
-- MySQL 8.4 / InnoDB / utf8mb4
-- All DATETIME(6) values are stored in UTC.

CREATE TABLE td_test_session (
                                 session_id CHAR(36) NOT NULL,
                                 mall_member_id VARCHAR(128) NOT NULL,
                                 definition_id CHAR(36) NOT NULL,
                                 apparel_scope VARCHAR(6) NOT NULL,

                                 status VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS',
                                 revision BIGINT NOT NULL DEFAULT 0,

                                 question_order JSON NOT NULL,

                                 last_seen_at DATETIME(6) NOT NULL,
                                 lease_until DATETIME(6) NOT NULL,
                                 started_at DATETIME(6) NOT NULL,
                                 submitted_at DATETIME(6) NULL,
                                 completed_at DATETIME(6) NULL,

                                 result_id CHAR(36) NULL,

                                 active_member_id VARCHAR(128)
                                     GENERATED ALWAYS AS (
                                         CASE
                                             WHEN status IN ('IN_PROGRESS', 'SUBMITTED')
                                                 THEN mall_member_id
                                             ELSE NULL
                                             END
                                         ) STORED,

                                 CONSTRAINT pk_td_test_session
                                     PRIMARY KEY (session_id),

                                 CONSTRAINT fk_td_test_session_definition
                                     FOREIGN KEY (definition_id)
                                         REFERENCES td_test_definition (definition_id)
                                         ON UPDATE RESTRICT
                                         ON DELETE RESTRICT,

                                 CONSTRAINT uq_td_test_session_active_member
                                     UNIQUE (active_member_id),

                                 CONSTRAINT uq_td_test_session_owner
                                     UNIQUE (session_id, mall_member_id),

                                 CONSTRAINT chk_td_test_session_scope
                                     CHECK (apparel_scope IN ('MENS', 'WOMENS')),

                                 CONSTRAINT chk_td_test_session_status
                                     CHECK (
                                         status IN (
                                                    'IN_PROGRESS',
                                                    'SUBMITTED',
                                                    'COMPLETED',
                                                    'FAILED',
                                                    'ABANDONED'
                                             )
                                         ),

                                 CONSTRAINT chk_td_test_session_revision
                                     CHECK (revision >= 0),

                                 INDEX idx_td_test_session_member_status
                                     (mall_member_id, status, last_seen_at)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE td_session_answer (
                                   session_id CHAR(36) NOT NULL,
                                   question_id VARCHAR(40) NOT NULL,

                                   answer_payload JSON NOT NULL,
                                   presented_payload JSON NOT NULL,

                                   answer_revision BIGINT NOT NULL,
                                   answered_at DATETIME(6) NOT NULL,

                                   CONSTRAINT pk_td_session_answer
                                       PRIMARY KEY (session_id, question_id),

                                   CONSTRAINT fk_td_session_answer_session
                                       FOREIGN KEY (session_id)
                                           REFERENCES td_test_session (session_id)
                                           ON UPDATE RESTRICT
                                           ON DELETE CASCADE,

                                   CONSTRAINT chk_td_session_answer_revision
                                       CHECK (answer_revision >= 1)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
