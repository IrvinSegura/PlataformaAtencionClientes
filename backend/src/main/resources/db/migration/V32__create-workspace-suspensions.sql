-- Workspaces whoever runs the platform suspended, e.g. for abuse or an unpaid invoice outside Stripe. While suspended,
-- the assistant answers nothing and hands every conversation to the team, as when the plan runs out. No row-level
-- security, like the workspaces themselves: the platform administration lists them across workspaces
CREATE TABLE workspace_suspensions (
    workspace_id UUID        PRIMARY KEY REFERENCES workspaces (id) ON DELETE CASCADE,
    reason       TEXT        NOT NULL,
    suspended_at TIMESTAMPTZ NOT NULL,
    suspended_by UUID        NOT NULL
);
