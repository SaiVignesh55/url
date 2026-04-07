-- Converts url_mapping.original_url from OID (large object) to TEXT if needed.
-- Safe to run multiple times; it only executes when column type is OID.
DO $$
DECLARE
    column_data_type text;
BEGIN
    SELECT c.data_type
    INTO column_data_type
    FROM information_schema.columns c
    WHERE c.table_schema = 'public'
      AND c.table_name = 'url_mapping'
      AND c.column_name = 'original_url';

    IF column_data_type = 'oid' THEN
        ALTER TABLE public.url_mapping
            ADD COLUMN original_url_tmp TEXT;

        UPDATE public.url_mapping
        SET original_url_tmp = CASE
            WHEN original_url IS NULL THEN NULL
            ELSE convert_from(lo_get(original_url), 'UTF8')
        END;

        ALTER TABLE public.url_mapping
            ALTER COLUMN original_url_tmp SET NOT NULL;

        ALTER TABLE public.url_mapping
            DROP COLUMN original_url;

        ALTER TABLE public.url_mapping
            RENAME COLUMN original_url_tmp TO original_url;
    END IF;
END
$$;
