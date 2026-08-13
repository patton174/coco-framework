-- H2 reference schema for coco-idempotency-jdbc. This module never executes DDL automatically.
-- Production applications must own schema changes through their migration process.
CREATE TABLE coco_idempotency (
    idempotency_key VARCHAR(128) NOT NULL PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    owner_token VARCHAR(64),
    status VARCHAR(16) NOT NULL,
    expires_at_epoch_millis BIGINT NOT NULL,
    response_status INTEGER,
    response_headers_json CLOB,
    response_body BLOB
);
