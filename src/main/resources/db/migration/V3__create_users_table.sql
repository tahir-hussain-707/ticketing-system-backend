CREATE TABLE users (
    id            UUID PRIMARY KEY,
    name          VARCHAR(200)  NOT NULL,
    email         VARCHAR(320)  NOT NULL,
    password_hash VARCHAR(200)  NOT NULL,
    role          VARCHAR(20)   NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL
);

CREATE UNIQUE INDEX idx_users_email ON users (LOWER(email));
CREATE INDEX idx_users_role ON users (role);
