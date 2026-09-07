-- ====================================================================
-- SEEKFACTORY ENTERPRISE DATABASE SCHEMA (POSTGRESQL 15+)
-- ====================================================================

-- 1. USERS TABLE
CREATE TABLE users (
    id VARCHAR(64) PRIMARY KEY,
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(50) UNIQUE,
    password_hash VARCHAR(255),
    role VARCHAR(32) NOT NULL DEFAULT 'ROLE_BUYER',
    auth_provider VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
    google_id VARCHAR(255) UNIQUE,
    name VARCHAR(255) NOT NULL,
    company_name VARCHAR(255),
    avatar_url TEXT,
    industry VARCHAR(255),
    country VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_role ON users(role);

-- 2. CATEGORIES TABLE
CREATE TABLE categories (
    id VARCHAR(64) PRIMARY KEY,
    slug VARCHAR(128) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    icon VARCHAR(64) NOT NULL DEFAULT 'other',
    parent_id VARCHAR(64) REFERENCES categories(id) ON DELETE SET NULL,
    listing_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_categories_slug ON categories(slug);
CREATE INDEX idx_categories_parent_id ON categories(parent_id);

-- 3. MANUFACTURERS TABLE
CREATE TABLE manufacturers (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    slug VARCHAR(128) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    logo_url TEXT,
    cover_url TEXT,
    country VARCHAR(100) NOT NULL,
    location VARCHAR(255) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    premium BOOLEAN NOT NULL DEFAULT FALSE,
    years_established INT NOT NULL DEFAULT 2000,
    factory_size VARCHAR(100),
    employees VARCHAR(100),
    description TEXT,
    follower_count INT NOT NULL DEFAULT 0,
    chairman_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_manufacturers_slug ON manufacturers(slug);
CREATE INDEX idx_manufacturers_verified ON manufacturers(verified);

-- 3a. MANUFACTURER EXPORT COUNTRIES (ElementCollection)
CREATE TABLE manufacturer_export_countries (
    manufacturer_id VARCHAR(64) NOT NULL REFERENCES manufacturers(id) ON DELETE CASCADE,
    country_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (manufacturer_id, country_name)
);

-- 3b. MANUFACTURER CATEGORIES (ManyToMany)
CREATE TABLE manufacturer_categories (
    manufacturer_id VARCHAR(64) NOT NULL REFERENCES manufacturers(id) ON DELETE CASCADE,
    category_id VARCHAR(64) NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    PRIMARY KEY (manufacturer_id, category_id)
);

-- 4. PRODUCTS TABLE
CREATE TABLE products (
    id VARCHAR(64) PRIMARY KEY,
    slug VARCHAR(128) UNIQUE NOT NULL,
    manufacturer_id VARCHAR(64) NOT NULL REFERENCES manufacturers(id) ON DELETE CASCADE,
    category_id VARCHAR(64) NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    name VARCHAR(255) NOT NULL,
    image_url TEXT NOT NULL,
    description TEXT,
    price_inr NUMERIC(12, 2) NOT NULL DEFAULT 0,
    unit VARCHAR(50) NOT NULL DEFAULT 'piece',
    moq VARCHAR(100) NOT NULL DEFAULT '1 piece',
    specs JSONB, -- Dynamic key-value pairs (Tolerance, LeadTime, Material, Certs)
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_products_slug ON products(slug);
CREATE INDEX idx_products_manufacturer_id ON products(manufacturer_id);
CREATE INDEX idx_products_category_id ON products(category_id);

-- 5. REELS TABLE (Video Showcase)
CREATE TABLE reels (
    id VARCHAR(64) PRIMARY KEY,
    manufacturer_id VARCHAR(64) NOT NULL REFERENCES manufacturers(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    poster_url TEXT NOT NULL,
    video_url TEXT,
    duration_sec INT NOT NULL DEFAULT 0,
    start_sec INT NOT NULL DEFAULT 0,
    views_count BIGINT NOT NULL DEFAULT 0,
    likes_count INT NOT NULL DEFAULT 0,
    comments_count INT NOT NULL DEFAULT 0,
    shares_count INT NOT NULL DEFAULT 0,
    saves_count INT NOT NULL DEFAULT 0,
    feed_tab VARCHAR(32) NOT NULL DEFAULT 'FOR_YOU',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reels_manufacturer_id ON reels(manufacturer_id);
CREATE INDEX idx_reels_feed_tab ON reels(feed_tab);

-- 5a. REEL HASHTAGS
CREATE TABLE reel_hashtags (
    reel_id VARCHAR(64) NOT NULL REFERENCES reels(id) ON DELETE CASCADE,
    hashtag VARCHAR(100) NOT NULL,
    PRIMARY KEY (reel_id, hashtag)
);

-- 5b. REEL FEATURED PRODUCTS
CREATE TABLE reel_products (
    reel_id VARCHAR(64) NOT NULL REFERENCES reels(id) ON DELETE CASCADE,
    product_id VARCHAR(64) NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    PRIMARY KEY (reel_id, product_id)
);

-- 5c. REEL LIKES & SAVES
CREATE TABLE reel_likes (
    id VARCHAR(64) PRIMARY KEY,
    reel_id VARCHAR(64) NOT NULL REFERENCES reels(id) ON DELETE CASCADE,
    user_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(reel_id, user_id)
);

CREATE TABLE reel_saves (
    id VARCHAR(64) PRIMARY KEY,
    reel_id VARCHAR(64) NOT NULL REFERENCES reels(id) ON DELETE CASCADE,
    user_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(reel_id, user_id)
);

-- 6. COMMENTS & REPLIES
CREATE TABLE comments (
    id VARCHAR(64) PRIMARY KEY,
    reel_id VARCHAR(64) NOT NULL REFERENCES reels(id) ON DELETE CASCADE,
    parent_id VARCHAR(64) REFERENCES comments(id) ON DELETE CASCADE,
    user_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    author_name VARCHAR(255) NOT NULL,
    author_avatar_url TEXT,
    author_company VARCHAR(255),
    author_country VARCHAR(100),
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    content TEXT NOT NULL,
    likes_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_comments_reel_id ON comments(reel_id);
CREATE INDEX idx_comments_parent_id ON comments(parent_id);

CREATE TABLE comment_likes (
    id VARCHAR(64) PRIMARY KEY,
    comment_id VARCHAR(64) NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    user_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(comment_id, user_id)
);

-- 7. RFQS TABLE (Request For Quotation)
CREATE TABLE rfqs (
    id VARCHAR(64) PRIMARY KEY,
    reference_number VARCHAR(64) UNIQUE NOT NULL,
    user_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_name VARCHAR(255) NOT NULL,
    category_id VARCHAR(64) REFERENCES categories(id) ON DELETE SET NULL,
    quantity VARCHAR(100) NOT NULL,
    unit VARCHAR(50) NOT NULL DEFAULT 'Pieces',
    target_price VARCHAR(100),
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    incoterm VARCHAR(20) NOT NULL DEFAULT 'FOB',
    company_name VARCHAR(255) NOT NULL,
    details TEXT NOT NULL,
    attachment_name VARCHAR(255),
    attachment_size VARCHAR(50),
    attachment_url TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rfqs_user_id ON rfqs(user_id);
CREATE INDEX idx_rfqs_status ON rfqs(status);

-- 7a. RFQ QUOTATIONS FROM SUPPLIERS
CREATE TABLE rfq_quotes (
    id VARCHAR(64) PRIMARY KEY,
    rfq_id VARCHAR(64) NOT NULL REFERENCES rfqs(id) ON DELETE CASCADE,
    manufacturer_id VARCHAR(64) NOT NULL REFERENCES manufacturers(id) ON DELETE CASCADE,
    quote_price NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'USD',
    lead_time_days INT NOT NULL,
    notes TEXT,
    attachment_url TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rfq_quotes_rfq_id ON rfq_quotes(rfq_id);
CREATE INDEX idx_rfq_quotes_manufacturer_id ON rfq_quotes(manufacturer_id);

-- 8. CONVERSATIONS & MESSAGES (B2B Chat)
CREATE TABLE conversations (
    id VARCHAR(64) PRIMARY KEY,
    buyer_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    manufacturer_id VARCHAR(64) NOT NULL REFERENCES manufacturers(id) ON DELETE CASCADE,
    last_message_text TEXT,
    last_message_at TIMESTAMP WITH TIME ZONE,
    unread_count_buyer INT NOT NULL DEFAULT 0,
    unread_count_supplier INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(buyer_id, manufacturer_id)
);

CREATE TABLE messages (
    id VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sender_type VARCHAR(32) NOT NULL, -- 'USER' or 'FACTORY'
    message_text TEXT NOT NULL,
    attachment_name VARCHAR(255),
    attachment_size VARCHAR(50),
    attachment_url TEXT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_messages_conversation_id ON messages(conversation_id);

-- 9. NOTIFICATIONS TABLE
CREATE TABLE notifications (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    notification_type VARCHAR(64) NOT NULL DEFAULT 'SYSTEM',
    reference_id VARCHAR(64),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_is_read ON notifications(is_read);