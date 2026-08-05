CREATE TABLE IF NOT EXISTS market_readonly_topic_index (
    topic_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL,
    discovered_at DATETIME NOT NULL,
    last_seen_at DATETIME NOT NULL,
    PRIMARY KEY (topic_id),
    KEY idx_market_readonly_feed (school_id, create_time, topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS market_readonly_sync_state (
    source_key VARCHAR(64) NOT NULL,
    next_page INT NOT NULL DEFAULT 1,
    mode VARCHAR(32) NOT NULL DEFAULT 'bootstrap',
    latest_watermark DATETIME NULL,
    latest_topic_id BIGINT NULL,
    last_success_at DATETIME NULL,
    last_error_code VARCHAR(64) NULL,
    consecutive_failures INT NOT NULL DEFAULT 0,
    last_fingerprint VARCHAR(128) NULL,
    PRIMARY KEY (source_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
