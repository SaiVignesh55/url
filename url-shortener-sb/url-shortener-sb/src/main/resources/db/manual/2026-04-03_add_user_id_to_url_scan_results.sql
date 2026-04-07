-- Safe migration for linking scan results to users.

ALTER TABLE url_scan_results
    ADD COLUMN IF NOT EXISTS user_id BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_url_scan_results_user_id
    ON url_scan_results (user_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_user_scan'
    ) THEN
        ALTER TABLE url_scan_results
            ADD CONSTRAINT fk_user_scan
                FOREIGN KEY (user_id) REFERENCES users (id)
                    ON DELETE CASCADE;
    END IF;
END $$;

-- Optional backfill example (only if you have a known fallback user id):
-- UPDATE url_scan_results SET user_id = 1 WHERE user_id IS NULL;

