DROP TABLE IF EXISTS sample_orders;

CREATE TABLE sample_orders (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    owner_id VARCHAR(64) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    amount BIGINT NOT NULL
);
