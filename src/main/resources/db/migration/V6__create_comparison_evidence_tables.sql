-- TasteDNA V6
-- Q08 comparison evidence metadata
-- MySQL 8.4 / InnoDB / utf8mb4

-- TODO(CODEX-DB-MIGRATION):
-- 기존 simple_q08_comparison_metadata_v0.1.json의
-- 비교 정의와 속성값은 추후 필요한 시점에 부분 이전한다.
-- JSON의 A/B에 임의의 스타일 속성을 부여하지 않는다.

CREATE TABLE td_comparison_definition (
                                          definition_id CHAR(36) NOT NULL,
                                          question_id VARCHAR(40) NOT NULL,
                                          comparison_id VARCHAR(40) NOT NULL,

                                          comparison_focus VARCHAR(80) NULL,
                                          evidence_status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',

                                          PRIMARY KEY (
                                                       definition_id,
                                                       question_id,
                                                       comparison_id
                                              ),

                                          CONSTRAINT fk_comparison_question
                                              FOREIGN KEY (definition_id, question_id)
                                                  REFERENCES td_question_definition (
                                                                                     definition_id, question_id
                                                      )
                                                  ON UPDATE RESTRICT
                                                  ON DELETE RESTRICT,

                                          CONSTRAINT chk_comparison_status
                                              CHECK (
                                                  evidence_status IN (
                                                                      'DRAFT',
                                                                      'ATTRIBUTES_DEFINED',
                                                                      'VERIFIED'
                                                      )
                                                  )

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE td_comparison_option_attribute (
                                                definition_id CHAR(36) NOT NULL,
                                                question_id VARCHAR(40) NOT NULL,
                                                comparison_id VARCHAR(40) NOT NULL,
                                                option_id VARCHAR(40) NOT NULL,

                                                visual_asset_id VARCHAR(100) NULL,
                                                attribute_key VARCHAR(80) NULL,
                                                attribute_value VARCHAR(120) NULL,

                                                PRIMARY KEY (
                                                             definition_id,
                                                             question_id,
                                                             comparison_id,
                                                             option_id
                                                    ),

                                                CONSTRAINT fk_comparison_option_definition
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

                                                CONSTRAINT chk_comparison_option_id
                                                    CHECK (option_id IN ('A', 'B')),

                                                CONSTRAINT chk_comparison_attribute_pair
                                                    CHECK (
                                                        (attribute_key IS NULL AND attribute_value IS NULL)
                                                            OR
                                                        (attribute_key IS NOT NULL AND attribute_value IS NOT NULL)
                                                        )

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
