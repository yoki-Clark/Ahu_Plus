CREATE TABLE IF NOT EXISTS market_readonly_topic_archive (
    topic_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    payload_codec VARCHAR(32) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    payload_bytes INT NOT NULL,
    payload_compressed MEDIUMBLOB NOT NULL,
    captured_at DATETIME NOT NULL,
    PRIMARY KEY (topic_id),
    KEY idx_market_readonly_archive_school (school_id, topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
