-- Ensure short_url is unique for both generated codes and custom aliases.
-- Run this after cleaning duplicate rows if your environment already has conflicts.
ALTER TABLE url_mapping
    ADD CONSTRAINT uk_url_mapping_short_url UNIQUE (short_url);

