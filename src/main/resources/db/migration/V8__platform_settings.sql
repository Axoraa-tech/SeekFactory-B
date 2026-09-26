-- V8__platform_settings.sql
-- (V7 is already used by V7__view_events.sql on another branch, applied to the shared dev DB)
-- Admin-managed, site-wide configuration stored as JSON per key.

CREATE TABLE IF NOT EXISTS platform_settings (
    key        VARCHAR(100) PRIMARY KEY,
    value      JSONB        NOT NULL,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Home feed seek showcase: DUAL keeps today's two-column feed
INSERT INTO platform_settings (key, value)
VALUES ('feed_showcase', '{"mode": "DUAL", "autoplay": true, "showProfile": true, "showPhotos": true}')
ON CONFLICT (key) DO NOTHING;
