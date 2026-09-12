-- Application users for JWT authentication. Two roles only: ANALYST reviews
-- alerts, ADMIN additionally manages users. Single role per user by design.
CREATE TABLE users
(
    id            BIGSERIAL PRIMARY KEY,

    -- UNIQUE here, not just a service-layer check: concurrent registrations
    -- both pass a "does it exist" query before either inserts.
    username      VARCHAR(50)  NOT NULL UNIQUE,

    -- Named _hash so that storing a plaintext value reads as obviously wrong.
    -- 60 chars is the fixed BCrypt output width, not an arbitrary cap.
    password_hash VARCHAR(60)  NOT NULL,

    -- VARCHAR + CHECK rather than a native ENUM type: adding a value later is
    -- a constraint change, not an ALTER TYPE. Values must match the Java enum
    -- constants exactly; EnumSyncTest guards that drift.
    role          VARCHAR(20)  NOT NULL,

    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT users_role_check CHECK (role IN ('ANALYST', 'ADMIN'))
);

COMMENT ON TABLE users IS 'Authentication principals; one role per user.';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hash. Never a plaintext password.';
