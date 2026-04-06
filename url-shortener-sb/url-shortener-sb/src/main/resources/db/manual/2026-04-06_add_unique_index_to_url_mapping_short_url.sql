-- Ensure short_url is unique for both generated codes and custom aliases.
-- This migration is rerunnable and will skip execution if:
-- 1) duplicates exist (manual cleanup required), or
-- 2) a unique index/constraint on short_url already exists.

SET @duplicate_short_url_count := (
    SELECT COUNT(*)
    FROM (
        SELECT short_url
        FROM url_mapping
        GROUP BY short_url
        HAVING COUNT(*) > 1
    ) duplicate_rows
);

SET @has_unique_short_url := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'url_mapping'
      AND column_name = 'short_url'
      AND non_unique = 0
);

SET @migration_sql := IF(
    @duplicate_short_url_count > 0,
    'SELECT ''SKIPPED: duplicate short_url values exist; clean data before adding UNIQUE'' AS message',
    IF(
        @has_unique_short_url = 0,
        'ALTER TABLE url_mapping ADD CONSTRAINT uk_url_mapping_short_url UNIQUE (short_url)',
        'SELECT ''SKIPPED: unique index/constraint on url_mapping.short_url already exists'' AS message'
    )
);

PREPARE stmt FROM @migration_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

