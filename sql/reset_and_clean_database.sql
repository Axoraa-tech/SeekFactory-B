-- ==============================================================================
-- SEEKFACTORY DATABASE CLEANUP & RESET SCRIPT
-- ==============================================================================
-- INSTRUCTIONS FOR SUPABASE:
-- 1. Open your Supabase Project Dashboard -> SQL Editor.
-- 2. Paste this entire script and click "RUN".
-- 3. This wipes all application data and resets Flyway migration history.
-- 4. Next time you start Spring Boot, Flyway will run cleanly from V1 to V3,
--    setting up all tables with fresh, verified demo data.
-- ==============================================================================

-- 1. Truncate all application data tables (in correct cascade order)
TRUNCATE TABLE 
    notifications,
    messages,
    conversations,
    rfq_quotes,
    rfqs,
    comment_likes,
    comments,
    reel_likes,
    reel_saves,
    reel_products,
    reel_hashtags,
    reels,
    products,
    manufacturer_export_countries,
    manufacturer_categories,
    manufacturers,
    categories,
    users,
    error_logs
CASCADE;

-- 2. Reset Flyway Schema History table so migrations run fresh from V1 to V3
DROP TABLE IF EXISTS flyway_schema_history CASCADE;

-- Confirmation query
SELECT 'Database successfully cleaned and reset for fresh Flyway migration!' AS status;
