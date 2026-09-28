-- Review and back up PostgreSQL before running this migration.
-- Execute in a maintenance window; do not run concurrently with application writes.
BEGIN;

CREATE TABLE IF NOT EXISTS scientific_records (
    resource_id varchar(255) PRIMARY KEY,
    revision bigint NOT NULL DEFAULT 0,
    status varchar(20) NOT NULL DEFAULT 'DRAFT',
    version_label varchar(40),
    version_doi varchar(255),
    conceptual_doi varchar(255),
    previous_resource_id varchar(255),
    license_id varchar(100),
    access_level varchar(30) DEFAULT 'OPEN',
    embargo_until timestamptz,
    language varchar(16),
    discipline varchar(255),
    keywords varchar(2000),
    orcid varchar(255),
    institution varchar(255),
    ror varchar(255),
    related_publications varchar(2000),
    methodology varchar(2000),
    submitted_at timestamptz,
    published_at timestamptz,
    withdrawn_at timestamptz,
    withdrawal_reason varchar(1000)
);

-- Safe when the table was created by an earlier revision of this branch.
ALTER TABLE scientific_records ADD COLUMN IF NOT EXISTS embargo_until timestamptz;

ALTER TABLE repo_users ADD COLUMN IF NOT EXISTS password_changed_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_scientific_records_status ON scientific_records(status);
CREATE INDEX IF NOT EXISTS idx_scientific_records_previous ON scientific_records(previous_resource_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_scientific_records_version_doi
    ON scientific_records(lower(version_doi)) WHERE version_doi IS NOT NULL;

COMMIT;
