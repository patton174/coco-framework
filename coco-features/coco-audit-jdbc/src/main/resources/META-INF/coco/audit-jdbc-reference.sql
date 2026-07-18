CREATE TABLE coco_audit_event (
    event_type CLOB NOT NULL,
    action CLOB NULL,
    resource_type CLOB NULL,
    resource_id CLOB NULL,
    trace_id CLOB NULL,
    actor CLOB NULL,
    tenant_id CLOB NULL,
    success BOOLEAN NOT NULL,
    occurred_at_epoch_millis BIGINT NOT NULL,
    attributes_json CLOB NOT NULL
);

CREATE INDEX idx_coco_audit_event_occurred_at
    ON coco_audit_event (occurred_at_epoch_millis);
