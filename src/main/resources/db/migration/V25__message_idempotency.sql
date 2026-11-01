ALTER TABLE messages ADD COLUMN client_message_id VARCHAR(64);
ALTER TABLE conversations ADD COLUMN message_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE messages ADD COLUMN change_revision BIGINT NOT NULL DEFAULT 0;
CREATE INDEX idx_messages_sync ON messages (conversation_id, change_revision);
CREATE UNIQUE INDEX uq_messages_client_send
    ON messages (conversation_id, sender_id, client_message_id)
    WHERE client_message_id IS NOT NULL;
