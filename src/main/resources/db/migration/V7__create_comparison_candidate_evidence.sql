-- TasteDNA V7
-- Q08 verified comparison attribute to RESULT candidate mapping
-- MySQL 8.4 / InnoDB / utf8mb4

-- TODO(CODEX-DB-MIGRATION):
-- 기존 JSON의 후보 속성 가설을 나중에 DB로 전환할 때
-- 관련 후보 매핑을 검토한다.
-- COMPATIBLE이나 미검증 가설을 VERIFIED로 자동 전환하지 않는다.

CREATE TABLE td_comparison_candidate_evidence (
                                                  definition_id CHAR(36) NOT NULL,
                                                  question_id VARCHAR(40) NOT NULL,
                                                  comparison_id VARCHAR(40) NOT NULL,

                                                  attribute_key VARCHAR(80) NOT NULL,
                                                  attribute_value VARCHAR(120) NOT NULL,

                                                  candidate_id VARCHAR(40) NOT NULL,

                                                  evidence_relation VARCHAR(24) NOT NULL,
                                                  verification_status VARCHAR(24) NOT NULL
                                                      DEFAULT 'PROPOSED',

                                                  PRIMARY KEY (
                                                               definition_id,
                                                               question_id,
                                                               comparison_id,
                                                               attribute_key,
                                                               attribute_value,
                                                               candidate_id
                                                      ),

                                                  CONSTRAINT fk_comparison_candidate_definition
                                                      FOREIGN KEY (
                                                                   definition_id,
                                                                   question_id,
                                                                   comparison_id
                                                          )
                                                          REFERENCES td_comparison_definition (
                                                                                               definition_id,
                                                                                               question_id,
                                                                                               comparison_id
                                                              )
                                                          ON UPDATE RESTRICT
                                                          ON DELETE RESTRICT,

                                                  CONSTRAINT chk_comparison_candidate_relation
                                                      CHECK (
                                                          evidence_relation IN (
                                                                                'SUPPORT',
                                                                                'COMPATIBLE'
                                                              )
                                                          ),

                                                  CONSTRAINT chk_comparison_candidate_verification
                                                      CHECK (
                                                          verification_status IN (
                                                                                  'PROPOSED',
                                                                                  'VERIFIED',
                                                                                  'REJECTED'
                                                              )
                                                          ),

                                                  CONSTRAINT chk_comparison_candidate_ids
                                                      CHECK (
                                                          CHAR_LENGTH(TRIM(attribute_key)) > 0
                                                              AND CHAR_LENGTH(TRIM(attribute_value)) > 0
                                                              AND CHAR_LENGTH(TRIM(candidate_id)) > 0
                                                          )

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
