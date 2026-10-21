CREATE TABLE client (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE balance (
    client_id BIGINT PRIMARY KEY,
    amount BIGINT NOT NULL,
    CONSTRAINT chk_balance_nonneg CHECK (amount >= 0),
    CONSTRAINT fk_balance_client FOREIGN KEY (client_id) REFERENCES client (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sender_number (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    phone VARCHAR(20) NOT NULL,
    status VARCHAR(16) NOT NULL,
    reject_reason VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_sender_phone UNIQUE (client_id, phone),
    CONSTRAINT fk_sender_client FOREIGN KEY (client_id) REFERENCES client (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE alimtalk_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    variables_json VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    CONSTRAINT uk_template_code UNIQUE (client_id, template_code),
    CONSTRAINT fk_template_client FOREIGN KEY (client_id) REFERENCES client (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE opt_out (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    phone VARCHAR(20) NOT NULL,
    CONSTRAINT uk_opt_out UNIQUE (client_id, phone),
    CONSTRAINT fk_opt_out_client FOREIGN KEY (client_id) REFERENCES client (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE campaign (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    channel VARCHAR(16) NOT NULL,
    purpose VARCHAR(16) NOT NULL,
    fallback_mode VARCHAR(16) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    fallback_body VARCHAR(4000) NULL,
    sender_number VARCHAR(20) NOT NULL,
    template_code VARCHAR(64) NULL,
    company_name VARCHAR(100) NULL,
    scheduled_at DATETIME(6) NULL,
    estimated_cost BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_campaign_client FOREIGN KEY (client_id) REFERENCES client (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    campaign_id BIGINT NOT NULL,
    recipient VARCHAR(20) NOT NULL,
    carrier VARCHAR(8) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    template_code VARCHAR(64) NULL,
    carrier_code VARCHAR(32) NULL,
    common_code VARCHAR(32) NULL,
    status VARCHAR(16) NOT NULL,
    fallback_of_id BIGINT NULL,
    cost BIGINT NOT NULL,
    reserved_cost BIGINT NOT NULL,
    purpose VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_message_campaign FOREIGN KEY (campaign_id) REFERENCES campaign (id),
    CONSTRAINT fk_message_fallback FOREIGN KEY (fallback_of_id) REFERENCES message (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_message_campaign_status ON message (campaign_id, status);
CREATE INDEX idx_message_created ON message (created_at);
CREATE INDEX idx_message_fallback ON message (fallback_of_id);

CREATE TABLE ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    campaign_id BIGINT NULL,
    message_id BIGINT NULL,
    entry_type VARCHAR(16) NOT NULL,
    amount BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_ledger_client FOREIGN KEY (client_id) REFERENCES client (id),
    CONSTRAINT fk_ledger_campaign FOREIGN KEY (campaign_id) REFERENCES campaign (id),
    CONSTRAINT fk_ledger_message FOREIGN KEY (message_id) REFERENCES message (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE export_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    campaign_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    file_path VARCHAR(500) NULL,
    error_message VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    CONSTRAINT fk_export_campaign FOREIGN KEY (campaign_id) REFERENCES campaign (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO client (id, name) VALUES (1, 'demo');
INSERT INTO balance (client_id, amount) VALUES (1, 1000000);
INSERT INTO sender_number (client_id, phone, status, created_at) VALUES (1, '0212345678', 'APPROVED', UTC_TIMESTAMP(6));
INSERT INTO alimtalk_template (client_id, template_code, body, variables_json, status)
VALUES (1, 'WELCOME', '안녕 #{name}', '["name"]', 'APPROVED');
INSERT INTO opt_out (client_id, phone) VALUES (1, '01099998888');
