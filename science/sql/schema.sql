-- Project Superhuman scientific data model v1
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS observations (
  id INTEGER PRIMARY KEY,
  kind TEXT NOT NULL,
  metric TEXT NOT NULL,
  value REAL NOT NULL,
  unit TEXT,
  observed_at INTEGER NOT NULL,
  source TEXT NOT NULL,
  source_confidence REAL,
  metadata_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_observations_metric_time ON observations(metric, observed_at);

CREATE TABLE IF NOT EXISTS clinical_reference_ranges (
  marker TEXT PRIMARY KEY,
  low REAL,
  high REAL,
  unit TEXT,
  source TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS marker_system_links (
  marker TEXT NOT NULL,
  body_system TEXT NOT NULL,
  weight REAL NOT NULL DEFAULT 1.0,
  PRIMARY KEY(marker, body_system)
);

CREATE TABLE IF NOT EXISTS scientific_insights (
  id INTEGER PRIMARY KEY,
  insight_type TEXT NOT NULL,
  status TEXT NOT NULL,
  confidence REAL NOT NULL,
  title TEXT NOT NULL,
  explanation TEXT NOT NULL,
  evidence_json TEXT NOT NULL,
  generated_at INTEGER NOT NULL,
  engine_version TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS engine_provenance (
  id INTEGER PRIMARY KEY,
  insight_id INTEGER NOT NULL,
  rule_id TEXT NOT NULL,
  rule_version TEXT NOT NULL,
  input_ids_json TEXT NOT NULL,
  FOREIGN KEY(insight_id) REFERENCES scientific_insights(id) ON DELETE CASCADE
);
