-- Indexes for the busiest per-user lists, ordered the way the repositories sort them.
-- Inbox polling and notification badges run on every page for signed-in users; without these,
-- each call scans and sorts a user's whole history. All small tables today, so plain CREATE INDEX
-- (inside Flyway's transaction) is quick.

-- Inbox: conversations of a buyer / of a factory, newest message first
CREATE INDEX IF NOT EXISTS idx_conversations_buyer_last_msg
    ON conversations (buyer_id, last_message_at DESC);
CREATE INDEX IF NOT EXISTS idx_conversations_manufacturer_last_msg
    ON conversations (manufacturer_id, last_message_at DESC);

-- A conversation's messages in order
CREATE INDEX IF NOT EXISTS idx_messages_conversation_created
    ON messages (conversation_id, created_at);

-- Notifications list and unread badge
CREATE INDEX IF NOT EXISTS idx_notifications_user_created
    ON notifications (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread
    ON notifications (user_id) WHERE is_read = false;

-- Catalogue and category pages: visible products, newest first
CREATE INDEX IF NOT EXISTS idx_products_category_created
    ON products (category_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_products_visible_created
    ON products (created_at DESC) WHERE is_active AND listed;

-- Seek feed: listed reels, newest first
CREATE INDEX IF NOT EXISTS idx_reels_listed_created
    ON reels (created_at DESC) WHERE listed;

-- Buyer and factory RFQ lists
CREATE INDEX IF NOT EXISTS idx_rfqs_user_created
    ON rfqs (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rfqs_category
    ON rfqs (category_id);
CREATE INDEX IF NOT EXISTS idx_rfq_quotes_manufacturer_created
    ON rfq_quotes (manufacturer_id, created_at DESC);

-- "Following" list
CREATE INDEX IF NOT EXISTS idx_manufacturer_follows_user_created
    ON manufacturer_follows (user_id, created_at DESC);
