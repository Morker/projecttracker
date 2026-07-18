CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    repo_url VARCHAR(500),
    demo_url VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS',
    tech_stack VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    owner_id BIGINT NOT NULL REFERENCES users(id)
);

CREATE TABLE features (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PLANNED',
    completed_at DATE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE
);

CREATE TABLE log_entries (
    id BIGSERIAL PRIMARY KEY,
    note TEXT NOT NULL,
    entry_date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE
);
