-- TasteDNA V5
-- Internal option scoring and observation rules
-- MySQL 8.4 / InnoDB / utf8mb4

CREATE TABLE td_option_scoring_rule (
                                        definition_id CHAR(36) NOT NULL,
                                        question_id VARCHAR(40) NOT NULL,
                                        option_id VARCHAR(40) NOT NULL,

                                        scoring_payload JSON NOT NULL,
                                        observation_payload JSON NULL,

                                        CONSTRAINT pk_td_option_scoring_rule
                                            PRIMARY KEY (
                                                         definition_id,
                                                         question_id,
                                                         option_id
                                                ),

                                        CONSTRAINT fk_td_option_scoring_rule_question
                                            FOREIGN KEY (
                                                         definition_id,
                                                         question_id
                                                )
                                                REFERENCES td_question_definition (
                                                                                   definition_id,
                                                                                   question_id
                                                    )
                                                ON UPDATE RESTRICT
                                                ON DELETE RESTRICT,

                                        CONSTRAINT chk_td_option_scoring_rule_option
                                            CHECK (CHAR_LENGTH(TRIM(option_id)) > 0)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
