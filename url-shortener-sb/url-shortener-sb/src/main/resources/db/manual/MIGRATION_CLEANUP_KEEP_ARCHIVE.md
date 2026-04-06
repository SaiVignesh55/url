# Manual Migration Keep vs Archive

This list documents which manual SQL scripts should remain operational and which are legacy cleanup scripts.

## KEEP (active schema requirements)

1. `2026-04-03_add_urlscan_metadata_columns.sql`
   - Required for `url_scan_results` metadata columns: `urlscan_scan_id`, `screenshot_url`, `redirect_chain`, `final_url`.
2. `2026-04-03_add_user_id_to_url_scan_results.sql`
   - Required to link `url_scan_results.user_id` to `users(id)`.
3. `2026-04-04_add_region_columns_to_click_event.sql`
   - Required for geo analytics columns in `click_event`: `country`, `region`, `city`.
4. `2026-04-04_add_region_columns_to_url_scan_results.sql`
   - Required for geo columns in `url_scan_results`: `country`, `region`, `city`.
5. `2026-04-06_add_unique_index_to_url_mapping_short_url.sql`
   - Critical: enforces uniqueness of `url_mapping.short_url` for generated codes and custom aliases.

## ARCHIVE (legacy / optional one-off)

1. `2026-04-04_add_user_region_columns_to_click_event.sql`
   - Legacy extension for `click_event.user_*` columns not used by current entity model.
2. `2026-04-04_fix_historical_user_region_data.sql`
   - One-time data correction tied to the legacy `user_*` columns above.

## Recommended execution order for new environments

1. `2026-04-03_add_urlscan_metadata_columns.sql`
2. `2026-04-03_add_user_id_to_url_scan_results.sql`
3. `2026-04-04_add_region_columns_to_click_event.sql`
4. `2026-04-04_add_region_columns_to_url_scan_results.sql`
5. `2026-04-06_add_unique_index_to_url_mapping_short_url.sql`

## Notes

- `2026-04-06_add_unique_index_to_url_mapping_short_url.sql` is idempotent and safe to rerun.
- If duplicate `short_url` values exist, the unique migration intentionally skips and prints a message so data can be cleaned first.

