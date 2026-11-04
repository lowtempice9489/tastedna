-- FORM 인증 회원만 저장한다. 기존 mall_member_id와 개발 계정의 데이터는 변경하지 않는다.
CREATE TABLE td_form_member (
    member_id VARCHAR(41) NOT NULL,
    email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    status VARCHAR(16) NOT NULL,
    role VARCHAR(16) NOT NULL,
    PRIMARY KEY (member_id),
    CONSTRAINT uq_form_member_email UNIQUE (email),
    CONSTRAINT chk_form_member_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_form_member_role CHECK (role = 'MEMBER')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
