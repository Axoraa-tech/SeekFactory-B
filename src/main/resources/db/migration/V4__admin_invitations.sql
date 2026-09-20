-- V4__admin_invitations.sql

CREATE TABLE admin_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    invited_by_id UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_admin_invitation_user FOREIGN KEY (invited_by_id) REFERENCES users (id) ON DELETE SET NULL
);

-- Pre-seed an invitation for the root admin
-- Token is fixed: 00000000-0000-0000-0000-000000000000
-- Expires far in the future so the user has time to set it up.
INSERT INTO admin_invitations (email, token, expires_at)
VALUES ('info.axoraa@gmail.com', '00000000-0000-0000-0000-000000000000', CURRENT_TIMESTAMP + INTERVAL '30 days');
