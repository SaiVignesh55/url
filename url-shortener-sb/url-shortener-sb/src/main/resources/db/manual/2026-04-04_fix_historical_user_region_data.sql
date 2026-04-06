-- One-time cleanup for legacy region analytics rows.
-- 1) Rows created using the old fallback 8.8.8.8 cannot be trusted; mark region unknown.
UPDATE click_event
SET user_region = 'UNKNOWN'
WHERE (user_region IS NULL OR TRIM(user_region) = '' OR UPPER(user_region) = 'UNKNOWN')
  AND (ip_address = '8.8.8.8' OR user_ip = '8.8.8.8');

-- 2) For older rows where region was accidentally saved in country,
--    backfill user_region from country when country and user_country differ.
UPDATE click_event
SET user_region = country
WHERE (user_region IS NULL OR TRIM(user_region) = '' OR UPPER(user_region) = 'UNKNOWN')
  AND country IS NOT NULL
  AND TRIM(country) <> ''
  AND UPPER(country) <> 'UNKNOWN'
  AND user_country IS NOT NULL
  AND TRIM(user_country) <> ''
  AND UPPER(user_country) <> 'UNKNOWN'
  AND LOWER(country) <> LOWER(user_country)
  AND COALESCE(ip_address, '') <> '8.8.8.8'
  AND COALESCE(user_ip, '') <> '8.8.8.8';

