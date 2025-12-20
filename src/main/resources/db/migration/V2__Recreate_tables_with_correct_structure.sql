
DROP TABLE IF EXISTS documents CASCADE;
DROP TABLE IF EXISTS users CASCADE;

CREATE TABLE users (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       username VARCHAR(255) UNIQUE NOT NULL,
                       password VARCHAR(255) NOT NULL,
                       account_non_expired BOOLEAN DEFAULT true,
                       account_non_locked BOOLEAN DEFAULT true,
                       credentials_non_expired BOOLEAN DEFAULT true,
                       enabled BOOLEAN DEFAULT true,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE documents (
                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           file_name VARCHAR(255) NOT NULL,
                           content TEXT,
                           user_id UUID NOT NULL,
                           created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                           CONSTRAINT fk_documents_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_documents_user_id ON documents(user_id);
CREATE INDEX idx_documents_created_at ON documents(created_at);