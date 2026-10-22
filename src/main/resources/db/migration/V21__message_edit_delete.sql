-- WhatsApp-style message edits and "delete for everyone".
-- A deleted message keeps its row (and place in the thread) with text and attachment cleared.
ALTER TABLE messages
    ADD COLUMN edited_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
