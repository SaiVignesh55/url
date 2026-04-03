-- Safe migration for linking scan results to users in urlshortenerdb
-- Run with: USE urlshortenerdb;

ALTER TABLE url_scan_results
    ADD COLUMN user_id BIGINT NULL;

ALTER TABLE url_scan_results
    ADD INDEX idx_url_scan_results_user_id (user_id);

ALTER TABLE url_scan_results
    ADD CONSTRAINT fk_user_scan
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE;

-- Optional backfill example (only if you have a known fallback user id):
-- UPDATE url_scan_results SET user_id = 1 WHERE user_id IS NULL;

