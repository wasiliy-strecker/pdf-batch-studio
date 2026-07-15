CREATE TABLE batch_jobs (
    id UUID PRIMARY KEY,
    status VARCHAR(40) NOT NULL,
    page_count INTEGER NOT NULL CHECK (page_count > 0),
    csv_delimiter VARCHAR(1) NOT NULL,
    total_rows INTEGER NOT NULL CHECK (total_rows > 0),
    filename_pattern VARCHAR(512) NOT NULL,
    processed_rows INTEGER NOT NULL DEFAULT 0 CHECK (processed_rows >= 0),
    successful_rows INTEGER NOT NULL DEFAULT 0 CHECK (successful_rows >= 0),
    failed_rows INTEGER NOT NULL DEFAULT 0 CHECK (failed_rows >= 0),
    failure_code VARCHAR(100),
    failure_message VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_batch_jobs_status ON batch_jobs (status);
CREATE INDEX idx_batch_jobs_expires_at ON batch_jobs (expires_at);

CREATE TABLE job_pdf_fields (
    job_id UUID NOT NULL REFERENCES batch_jobs (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    field_name VARCHAR(512) NOT NULL,
    field_type VARCHAR(100) NOT NULL,
    supported BOOLEAN NOT NULL,
    PRIMARY KEY (job_id, position)
);

CREATE TABLE job_csv_headers (
    job_id UUID NOT NULL REFERENCES batch_jobs (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    header_name VARCHAR(512) NOT NULL,
    PRIMARY KEY (job_id, position)
);

CREATE TABLE job_field_mappings (
    job_id UUID NOT NULL REFERENCES batch_jobs (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    pdf_field VARCHAR(512) NOT NULL,
    csv_column VARCHAR(512) NOT NULL,
    PRIMARY KEY (job_id, position),
    UNIQUE (job_id, pdf_field)
);
