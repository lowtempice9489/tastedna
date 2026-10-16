
-- TasteDNA V3
-- Test definitions and question definitions
-- MySQL 8.4 / InnoDB / utf8mb4

CREATE TABLE td_test_definition (
                                    definition_id CHAR(36) NOT NULL,

                                    test_mode VARCHAR(6) NOT NULL,
                                    version_code VARCHAR(40) NOT NULL,

                                    question_count SMALLINT NOT NULL,

                                    scoring_version VARCHAR(40) NOT NULL,
                                    raw_schema_version VARCHAR(40) NOT NULL,
                                    equivalence_version VARCHAR(40) NULL,

                                    published_at DATETIME(6) NOT NULL,
                                    active BOOLEAN NOT NULL DEFAULT FALSE,

    -- MySQL에서 모드별 활성 정의 1개 제한
                                    active_mode VARCHAR(6)
                                        GENERATED ALWAYS AS (
                                            CASE
                                                WHEN active = 1 THEN test_mode
                                                ELSE NULL
                                                END
                                            ) STORED,

                                    CONSTRAINT pk_td_test_definition
                                        PRIMARY KEY (definition_id),

                                    CONSTRAINT uq_td_test_definition_version
                                        UNIQUE (test_mode, version_code),

                                    CONSTRAINT uq_td_test_definition_active_mode
                                        UNIQUE (active_mode),

                                    CONSTRAINT chk_td_test_definition_mode
                                        CHECK (test_mode IN ('SIMPLE', 'DEEP')),

                                    CONSTRAINT chk_td_test_definition_count
                                        CHECK (
                                            (test_mode = 'SIMPLE' AND question_count = 10)
                                                OR
                                            (test_mode = 'DEEP' AND question_count = 25)
                                            ),

                                    CONSTRAINT chk_td_test_definition_active
                                        CHECK (active IN (0, 1))

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE td_question_definition (
                                        definition_id CHAR(36) NOT NULL,

                                        question_id VARCHAR(40) NOT NULL,
                                        display_order SMALLINT NOT NULL,

                                        question_payload JSON NOT NULL,
                                        dependency_payload JSON NOT NULL,

                                        required BOOLEAN NOT NULL DEFAULT TRUE,

                                        CONSTRAINT pk_td_question_definition
                                            PRIMARY KEY (definition_id, question_id),

                                        CONSTRAINT uq_td_question_definition_order
                                            UNIQUE (definition_id, display_order),

                                        CONSTRAINT fk_td_question_definition_test
                                            FOREIGN KEY (definition_id)
                                                REFERENCES td_test_definition (definition_id)
                                                ON UPDATE RESTRICT
                                                ON DELETE RESTRICT,

                                        CONSTRAINT chk_td_question_definition_order
                                            CHECK (display_order > 0),

                                        CONSTRAINT chk_td_question_definition_required
                                            CHECK (required IN (0, 1))

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
