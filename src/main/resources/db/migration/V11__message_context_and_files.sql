-- V11__message_context_and_files.sql
-- Chat messages can reference the order they are about, and carry the content type of
-- an uploaded image/PDF attachment. Additive only.

ALTER TABLE messages ADD COLUMN order_id VARCHAR(64) REFERENCES order_requests(id) ON DELETE SET NULL;
ALTER TABLE messages ADD COLUMN attachment_content_type VARCHAR(100);

CREATE INDEX idx_messages_order_id ON messages (order_id);
