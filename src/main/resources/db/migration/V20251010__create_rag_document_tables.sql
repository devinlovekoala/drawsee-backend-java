CREATE TABLE IF NOT EXISTS knowledge_document (
    id                VARCHAR(64) PRIMARY KEY,
    knowledge_base_id VARCHAR(255) NOT NULL,
    title             VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(255),
    file_type         VARCHAR(128),
    file_size         BIGINT,
    page_count        INT,
    status            VARCHAR(32) NOT NULL,
    chunk_count       INT DEFAULT 0,
    storage_url       VARCHAR(512),
    storage_object    VARCHAR(512),
    uploader_id       BIGINT,
    uploaded_at       DATETIME,
    processed_at      DATETIME,
    failure_reason    TEXT,
    created_at        DATETIME NOT NULL,
    updated_at        DATETIME NOT NULL,
    is_deleted        TINYINT(1) DEFAULT 0,
    CONSTRAINT fk_kd_kb_id FOREIGN KEY (knowledge_base_id)
        REFERENCES knowledge_base(id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS knowledge_document_chunk (
    id                 VARCHAR(64) PRIMARY KEY,
    document_id        VARCHAR(64) NOT NULL,
    knowledge_base_id  VARCHAR(255) NOT NULL,
    chunk_index        INT NOT NULL,
    content            MEDIUMTEXT,
    token_count        INT,
    vector_id          VARCHAR(128),
    vector_dimension   INT,
    created_at         DATETIME NOT NULL,
    updated_at         DATETIME NOT NULL,
    CONSTRAINT fk_kdc_doc_id FOREIGN KEY (document_id)
        REFERENCES knowledge_document(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_kdc_kb_id FOREIGN KEY (knowledge_base_id)
        REFERENCES knowledge_base(id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS rag_ingestion_task (
    id                 VARCHAR(64) PRIMARY KEY,
    knowledge_base_id  VARCHAR(255) NOT NULL,
    document_id        VARCHAR(64) NOT NULL,
    stage              VARCHAR(32) NOT NULL,
    status             VARCHAR(32) NOT NULL,
    progress           INT DEFAULT 0,
    error_message      TEXT,
    duration_ms        BIGINT,
    created_at         DATETIME NOT NULL,
    updated_at         DATETIME NOT NULL,
    completed_at       DATETIME,
    CONSTRAINT fk_rit_doc_id FOREIGN KEY (document_id)
        REFERENCES knowledge_document(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_rit_kb_id FOREIGN KEY (knowledge_base_id)
        REFERENCES knowledge_base(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_kd_kb_status
    ON knowledge_document (knowledge_base_id, status);
CREATE INDEX IF NOT EXISTS idx_kd_created_at
    ON knowledge_document (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_kdc_doc_idx
    ON knowledge_document_chunk (document_id, chunk_index);
CREATE INDEX IF NOT EXISTS idx_rit_status
    ON rag_ingestion_task (status, created_at);
