-- Ensure short_url is unique for both generated codes and custom aliases.
-- This migration is rerunnable and will skip execution if:
-- 1) duplicates exist (manual cleanup required), or
-- 2) a unique index/constraint on short_url already exists.

DO $$
DECLARE
    duplicate_short_url_count INTEGER;
    has_unique_short_url BOOLEAN;
BEGIN
    SELECT COUNT(*)
    INTO duplicate_short_url_count
    FROM (
        SELECT short_url
        FROM url_mapping
        GROUP BY short_url
        HAVING COUNT(*) > 1
    ) duplicate_rows;

    SELECT EXISTS (
        SELECT 1
        FROM pg_constraint c
                 JOIN pg_class t ON c.conrelid = t.oid
                 JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE c.contype = 'u'
          AND n.nspname = current_schema()
          AND t.relname = 'url_mapping'
          AND c.conname = 'uk_url_mapping_short_url'
    )
    INTO has_unique_short_url;

    IF duplicate_short_url_count > 0 THEN
        RAISE NOTICE 'SKIPPED: duplicate short_url values exist; clean data before adding UNIQUE';
    ELSIF NOT has_unique_short_url THEN
        ALTER TABLE url_mapping
            ADD CONSTRAINT uk_url_mapping_short_url UNIQUE (short_url);
    ELSE
        RAISE NOTICE 'SKIPPED: unique constraint uk_url_mapping_short_url already exists';
    END IF;
END $$;

