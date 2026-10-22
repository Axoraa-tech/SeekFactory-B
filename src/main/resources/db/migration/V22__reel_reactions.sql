-- V22__reel_reactions.sql
-- A seek "like" now carries a reaction type: LIKE (thumbs up), LOVE (heart) or STAR.
-- Existing likes become LIKE; one reaction per user per reel (the existing unique key).
ALTER TABLE reel_likes
    ADD COLUMN IF NOT EXISTS reaction VARCHAR(16) NOT NULL DEFAULT 'LIKE';

ALTER TABLE reel_likes DROP CONSTRAINT IF EXISTS chk_reel_likes_reaction;
ALTER TABLE reel_likes
    ADD CONSTRAINT chk_reel_likes_reaction CHECK (reaction IN ('LIKE', 'LOVE', 'STAR'));
