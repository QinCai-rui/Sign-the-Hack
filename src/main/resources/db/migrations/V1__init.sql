CREATE TABLE IF NOT EXISTS scans (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scan_uuid TEXT NOT NULL UNIQUE,
    target_uuid TEXT NOT NULL,
    target_name TEXT NOT NULL,
    checker_name TEXT NOT NULL,
    reason TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS check_results (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scan_id INTEGER NOT NULL,
    check_id TEXT NOT NULL,
    check_name TEXT NOT NULL,
    status TEXT NOT NULL,
    detail TEXT,
    FOREIGN KEY(scan_id) REFERENCES scans(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS punishment_audit (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scan_id INTEGER NOT NULL,
    actions TEXT NOT NULL,
    rendered_reason TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY(scan_id) REFERENCES scans(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_scans_target_created ON scans(target_uuid, created_at);
CREATE INDEX IF NOT EXISTS idx_scans_reason_created ON scans(reason, created_at);
CREATE INDEX IF NOT EXISTS idx_results_scan_status ON check_results(scan_id, status);
CREATE INDEX IF NOT EXISTS idx_punishment_scan ON punishment_audit(scan_id);
