-- V28__account_deletion.sql (V27 is taken by perf/indexes-and-variant-misses)
-- Self-service account deletion (App Store 5.1.1(v), Google Play) anonymises the user row
-- instead of deleting it: every foreign key to users is ON DELETE CASCADE, so a hard delete
-- would also wipe the chats, orders and RFQs the other party still sees.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
