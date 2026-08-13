-- Reference schema for coco-idempotency-jdbc. Do not execute automatically.
-- Production applications must manage this table through their own Flyway or Liquibase migration.
CREATE TABLE coco_idempotency (
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    owner_token VARCHAR(64),
    status VARCHAR(16) NOT NULL,
    expires_at_epoch_millis BIGINT NOT NULL,
    response_status INTEGER,
    response_headers_json CLOB,
    response_body BLOB,
    PRIMARY KEY (idempotency_key)
);
