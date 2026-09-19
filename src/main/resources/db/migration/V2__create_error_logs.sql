CREATE TABLE error_logs (
    id VARCHAR(64) PRIMARY KEY,
    error_type VARCHAR(255) NOT NULL,
    message TEXT,
    stack_trace TEXT,
    http_method VARCHAR(10),
    endpoint VARCHAR(500),
    status_code INT,
    user_id VARCHAR(64),
    ip_address VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_error_logs_created_at ON error_logs(created_at DESC);
CREATE INDEX idx_error_logs_status_code ON error_logs(status_code);
CREATE INDEX idx_error_logs_user_id ON error_logs(user_id);