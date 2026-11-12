CREATE TABLE organizations (
    id          UUID PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE organization_members (
    organization_id UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL,
    role            VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'MANAGER')),
    PRIMARY KEY (organization_id, user_id)
);

-- BR-14: mỗi tổ chức có đúng 1 OWNER.
CREATE UNIQUE INDEX organization_members_one_owner ON organization_members (organization_id) WHERE role = 'OWNER';
CREATE INDEX organization_members_user_idx ON organization_members (user_id);

ALTER TABLE events ADD COLUMN organization_id UUID REFERENCES organizations (id);
CREATE INDEX events_organization_idx ON events (organization_id);

CREATE TABLE event_staff (
    event_id UUID NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    user_id  UUID NOT NULL,
    PRIMARY KEY (event_id, user_id)
);
CREATE INDEX event_staff_user_idx ON event_staff (user_id);
