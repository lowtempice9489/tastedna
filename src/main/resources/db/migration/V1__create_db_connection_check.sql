
CREATE TABLE td_db_connection_check (
                                        id BIGINT NOT NULL AUTO_INCREMENT,
                                        check_message VARCHAR(100) NOT NULL,
                                        created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                        PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
