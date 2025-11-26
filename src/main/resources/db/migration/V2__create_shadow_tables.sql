-- Shadow tables for ScaleKit users and organizations
-- These tables maintain local copies for referential integrity (FK constraints)

CREATE TABLE users (
    id VARCHAR(255) PRIMARY KEY,  -- ScaleKit user ID (used as primary key)
    scalekit_id VARCHAR(255) UNIQUE NOT NULL,  -- Redundant but explicit
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE organizations (
    id VARCHAR(255) PRIMARY KEY,  -- ScaleKit organization ID (used as primary key)
    scalekit_org_id VARCHAR(255) UNIQUE NOT NULL,  -- Redundant but explicit
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_roles (
    user_id VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id VARCHAR(255) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    role_name VARCHAR(255) NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, organization_id, role_name)
);

-- Indexes for performance
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_scalekit_id ON users(scalekit_id);
CREATE INDEX idx_users_active ON users(active);
CREATE INDEX idx_organizations_scalekit_org_id ON organizations(scalekit_org_id);
CREATE INDEX idx_organizations_active ON organizations(active);
CREATE INDEX idx_user_roles_user_org ON user_roles(user_id, organization_id);
CREATE INDEX idx_user_roles_org ON user_roles(organization_id);

