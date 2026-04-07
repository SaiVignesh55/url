-- Safe migration for urlscan metadata persistence in url_scan_results.

ALTER TABLE url_scan_results
    ADD COLUMN IF NOT EXISTS urlscan_scan_id VARCHAR(128) NOT NULL DEFAULT '';

ALTER TABLE url_scan_results
    ADD COLUMN IF NOT EXISTS screenshot_url VARCHAR(2048) NOT NULL DEFAULT '';

ALTER TABLE url_scan_results
    ADD COLUMN IF NOT EXISTS redirect_chain TEXT NULL;

ALTER TABLE url_scan_results
    ADD COLUMN IF NOT EXISTS final_url VARCHAR(2048) NOT NULL DEFAULT '';

-- Backfill final_url for older rows
UPDATE url_scan_results
SET final_url = COALESCE(NULLIF(url, ''), COALESCE(NULLIF(scanned_url, ''), 'unknown'))
WHERE final_url IS NULL OR final_url = '';

