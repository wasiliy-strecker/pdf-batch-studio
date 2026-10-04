CREATE TABLE templates (
    id TEXT PRIMARY KEY,
    path TEXT NOT NULL UNIQUE
);
CREATE TABLE projects (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    template_id TEXT NOT NULL REFERENCES templates(id),
    filename_pattern TEXT NOT NULL,
    sheet TEXT NOT NULL DEFAULT '',
    folder_export INTEGER NOT NULL DEFAULT 0,
    updated_at TEXT NOT NULL
);
CREATE TABLE field_mappings (
    project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    pdf_field TEXT NOT NULL,
    csv_column TEXT NOT NULL,
    PRIMARY KEY (project_id, pdf_field)
);
CREATE TABLE batch_runs (
    id TEXT PRIMARY KEY,
    project_id TEXT REFERENCES projects(id) ON DELETE SET NULL,
    project_name TEXT NOT NULL,
    status TEXT NOT NULL,
    started_at TEXT NOT NULL,
    finished_at TEXT,
    processed INTEGER NOT NULL DEFAULT 0,
    successful INTEGER NOT NULL DEFAULT 0,
    failed INTEGER NOT NULL DEFAULT 0,
    result_path TEXT
);
CREATE INDEX idx_batch_runs_started ON batch_runs(started_at);
CREATE TABLE batch_errors (
    run_id TEXT NOT NULL REFERENCES batch_runs(id) ON DELETE CASCADE,
    row_number INTEGER NOT NULL,
    code TEXT NOT NULL,
    message TEXT NOT NULL,
    PRIMARY KEY (run_id, row_number)
);
CREATE TABLE app_settings (name TEXT PRIMARY KEY, value TEXT NOT NULL);
