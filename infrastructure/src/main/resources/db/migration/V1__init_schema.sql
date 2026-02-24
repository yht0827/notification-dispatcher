-- =====================
-- Tables
-- =====================

CREATE TABLE notification_group
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id       VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NULL,
    sender          VARCHAR(255) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    content         TEXT         NOT NULL,
    group_type      VARCHAR(50)  NOT NULL,
    channel_type    VARCHAR(50)  NOT NULL,
    total_count     INT          NOT NULL DEFAULT 0,
    sent_count      INT          NOT NULL DEFAULT 0,
    failed_count    INT          NOT NULL DEFAULT 0,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL
);

CREATE TABLE notification
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id      BIGINT       NULL,
    receiver      VARCHAR(255) NOT NULL,
    status        VARCHAR(50)  NOT NULL,
    sent_at       DATETIME(6)  NULL,
    attempt_count INT          NOT NULL DEFAULT 0,
    fail_reason   VARCHAR(500) NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    deleted_at    DATETIME(6)  NULL
);

CREATE TABLE outbox
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id   BIGINT       NOT NULL,
    event_type     VARCHAR(255) NOT NULL,
    payload        TEXT         NULL,
    status         VARCHAR(50)  NOT NULL,
    processed_at   DATETIME(6)  NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    deleted_at     DATETIME(6)  NULL
);

-- =====================
-- Indexes
-- =====================

-- notification_group
CREATE UNIQUE INDEX idx_notification_group_client_idempotency_key ON notification_group (client_id, idempotency_key);
CREATE INDEX idx_notification_group_client_id ON notification_group (client_id);
CREATE INDEX idx_notification_group_group_type ON notification_group (group_type);
CREATE INDEX idx_notification_group_deleted_at ON notification_group (deleted_at);
CREATE INDEX idx_notification_group_client_created ON notification_group (client_id, created_at);

-- notification
CREATE INDEX idx_notification_group_id ON notification (group_id);
CREATE INDEX idx_notification_receiver ON notification (receiver);
CREATE INDEX idx_notification_deleted_at ON notification (deleted_at);
CREATE INDEX idx_notification_receiver_status ON notification (receiver, status);
CREATE INDEX idx_notification_status_created ON notification (status, created_at);
CREATE INDEX idx_notification_group_status ON notification (group_id, status);

CREATE INDEX idx_outbox_status_created ON outbox (status, created_at);