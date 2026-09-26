-- V7__view_events.sql
-- Timestamped view events powering seller analytics (seek impressions, product views,
-- period-over-period change). Additive only: no existing table is modified.
--
-- manufacturer_id is denormalised from the viewed reel/product so dashboard queries
-- are a single indexed range scan per factory.

CREATE TABLE view_events (
    id VARCHAR(64) PRIMARY KEY,
    entity_type VARCHAR(16) NOT NULL,                 -- 'REEL' | 'PRODUCT'
    entity_id VARCHAR(64) NOT NULL,
    manufacturer_id VARCHAR(64) NOT NULL REFERENCES manufacturers(id) ON DELETE CASCADE,
    viewer_key VARCHAR(128) NOT NULL,                 -- 'u:<userId>' or 'a:<anonymous id>'
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_view_events_entity_type CHECK (entity_type IN ('REEL', 'PRODUCT'))
);

-- Dashboard: count a factory's views of one type within a time window
CREATE INDEX idx_view_events_mfr_type_time ON view_events (manufacturer_id, entity_type, created_at);

-- Dedupe: has this viewer seen this entity recently?
CREATE INDEX idx_view_events_dedupe ON view_events (entity_type, entity_id, viewer_key, created_at);
