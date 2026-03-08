-- =============================================
-- Main Tables
-- =============================================

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
    updated_at      DATETIME(6)  NOT NULL
);

CREATE UNIQUE INDEX idx_notification_group_client_idempotency_key ON notification_group (client_id, idempotency_key);
CREATE INDEX idx_notification_group_client_id ON notification_group (client_id);
CREATE INDEX idx_notification_group_group_type ON notification_group (group_type);
CREATE INDEX idx_notification_group_client_created ON notification_group (client_id, created_at);

-- ---------------------------------------------

CREATE TABLE notification
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id      BIGINT       NULL,
    receiver      VARCHAR(255) NOT NULL,
    status        VARCHAR(50)  NOT NULL,
    sent_at       DATETIME(6)  NULL,
    attempt_count INT          NOT NULL DEFAULT 0,
    fail_reason   VARCHAR(500) NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL
);

CREATE INDEX idx_notification_group_id ON notification (group_id);
CREATE INDEX idx_notification_receiver ON notification (receiver);
CREATE INDEX idx_notification_receiver_status ON notification (receiver, status);
CREATE INDEX idx_notification_status_created ON notification (status, created_at);
CREATE INDEX idx_notification_group_status ON notification (group_id, status);

-- ---------------------------------------------

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
    updated_at     DATETIME(6)  NOT NULL
);

CREATE INDEX idx_outbox_status_created ON outbox (status, created_at);

-- ---------------------------------------------

CREATE TABLE notification_read_status
(
    notification_id BIGINT      NOT NULL PRIMARY KEY,
    read_at         DATETIME(6) NOT NULL,
    CONSTRAINT fk_notification_read_status_notification
        FOREIGN KEY (notification_id) REFERENCES notification (id)
);

CREATE INDEX idx_notification_read_status_read_at ON notification_read_status (read_at);

-- =============================================
-- Archive Tables (월별 RANGE 파티션)
-- =============================================

CREATE TABLE notification_archive
(
    id            BIGINT       NOT NULL,
    group_id      BIGINT       NULL,
    receiver      VARCHAR(255) NOT NULL,
    status        VARCHAR(50)  NOT NULL,
    sent_at       DATETIME(6)  NULL,
    attempt_count INT          NOT NULL DEFAULT 0,
    fail_reason   VARCHAR(500) NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    archived_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id, created_at)
)
    PARTITION BY RANGE (YEAR(created_at) * 100 + MONTH(created_at)) (
        PARTITION p202601 VALUES LESS THAN (202602),
        PARTITION p202602 VALUES LESS THAN (202603),
        PARTITION p202603 VALUES LESS THAN (202604),
        PARTITION p202604 VALUES LESS THAN (202605),
        PARTITION p202605 VALUES LESS THAN (202606),
        PARTITION p202606 VALUES LESS THAN (202607),
        PARTITION p202607 VALUES LESS THAN (202608),
        PARTITION p202608 VALUES LESS THAN (202609),
        PARTITION p202609 VALUES LESS THAN (202610),
        PARTITION p202610 VALUES LESS THAN (202611),
        PARTITION p202611 VALUES LESS THAN (202612),
        PARTITION p202612 VALUES LESS THAN (202701),
        PARTITION p202701 VALUES LESS THAN (202702),
        PARTITION p202702 VALUES LESS THAN (202703),
        PARTITION p202703 VALUES LESS THAN (202704),
        PARTITION p202704 VALUES LESS THAN (202705),
        PARTITION p202705 VALUES LESS THAN (202706),
        PARTITION p202706 VALUES LESS THAN (202707),
        PARTITION p202707 VALUES LESS THAN (202708),
        PARTITION p202708 VALUES LESS THAN (202709),
        PARTITION p202709 VALUES LESS THAN (202710),
        PARTITION p202710 VALUES LESS THAN (202711),
        PARTITION p202711 VALUES LESS THAN (202712),
        PARTITION p202712 VALUES LESS THAN (202801),
        PARTITION p_future VALUES LESS THAN MAXVALUE
        );

CREATE INDEX idx_notification_archive_group_id ON notification_archive (group_id);
CREATE INDEX idx_notification_archive_status_created ON notification_archive (status, created_at);
CREATE INDEX idx_notification_archive_archived_at ON notification_archive (archived_at);

-- ---------------------------------------------

CREATE TABLE notification_group_archive
(
    id              BIGINT       NOT NULL,
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
    archived_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id, created_at)
)
    PARTITION BY RANGE (YEAR(created_at) * 100 + MONTH(created_at)) (
        PARTITION p202601 VALUES LESS THAN (202602),
        PARTITION p202602 VALUES LESS THAN (202603),
        PARTITION p202603 VALUES LESS THAN (202604),
        PARTITION p202604 VALUES LESS THAN (202605),
        PARTITION p202605 VALUES LESS THAN (202606),
        PARTITION p202606 VALUES LESS THAN (202607),
        PARTITION p202607 VALUES LESS THAN (202608),
        PARTITION p202608 VALUES LESS THAN (202609),
        PARTITION p202609 VALUES LESS THAN (202610),
        PARTITION p202610 VALUES LESS THAN (202611),
        PARTITION p202611 VALUES LESS THAN (202612),
        PARTITION p202612 VALUES LESS THAN (202701),
        PARTITION p202701 VALUES LESS THAN (202702),
        PARTITION p202702 VALUES LESS THAN (202703),
        PARTITION p202703 VALUES LESS THAN (202704),
        PARTITION p202704 VALUES LESS THAN (202705),
        PARTITION p202705 VALUES LESS THAN (202706),
        PARTITION p202706 VALUES LESS THAN (202707),
        PARTITION p202707 VALUES LESS THAN (202708),
        PARTITION p202708 VALUES LESS THAN (202709),
        PARTITION p202709 VALUES LESS THAN (202710),
        PARTITION p202710 VALUES LESS THAN (202711),
        PARTITION p202711 VALUES LESS THAN (202712),
        PARTITION p202712 VALUES LESS THAN (202801),
        PARTITION p_future VALUES LESS THAN MAXVALUE
        );

CREATE INDEX idx_notification_group_archive_client_created ON notification_group_archive (client_id, created_at);
CREATE INDEX idx_notification_group_archive_archived_at ON notification_group_archive (archived_at);

-- ---------------------------------------------

CREATE TABLE notification_read_status_archive
(
    notification_id BIGINT      NOT NULL,
    read_at         DATETIME(6) NOT NULL,
    archived_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (notification_id, read_at)
)
    PARTITION BY RANGE (YEAR(read_at) * 100 + MONTH(read_at)) (
        PARTITION p202601 VALUES LESS THAN (202602),
        PARTITION p202602 VALUES LESS THAN (202603),
        PARTITION p202603 VALUES LESS THAN (202604),
        PARTITION p202604 VALUES LESS THAN (202605),
        PARTITION p202605 VALUES LESS THAN (202606),
        PARTITION p202606 VALUES LESS THAN (202607),
        PARTITION p202607 VALUES LESS THAN (202608),
        PARTITION p202608 VALUES LESS THAN (202609),
        PARTITION p202609 VALUES LESS THAN (202610),
        PARTITION p202610 VALUES LESS THAN (202611),
        PARTITION p202611 VALUES LESS THAN (202612),
        PARTITION p202612 VALUES LESS THAN (202701),
        PARTITION p202701 VALUES LESS THAN (202702),
        PARTITION p202702 VALUES LESS THAN (202703),
        PARTITION p202703 VALUES LESS THAN (202704),
        PARTITION p202704 VALUES LESS THAN (202705),
        PARTITION p202705 VALUES LESS THAN (202706),
        PARTITION p202706 VALUES LESS THAN (202707),
        PARTITION p202707 VALUES LESS THAN (202708),
        PARTITION p202708 VALUES LESS THAN (202709),
        PARTITION p202709 VALUES LESS THAN (202710),
        PARTITION p202710 VALUES LESS THAN (202711),
        PARTITION p202711 VALUES LESS THAN (202712),
        PARTITION p202712 VALUES LESS THAN (202801),
        PARTITION p_future VALUES LESS THAN MAXVALUE
        );

CREATE INDEX idx_notification_read_status_archive_archived_at ON notification_read_status_archive (archived_at);
