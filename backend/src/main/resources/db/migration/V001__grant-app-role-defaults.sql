-- Tables created by the migration owner are readable and writable by the application role, which owns nothing:
-- an owner bypasses row-level security, so the application must never connect as one.
-- The role is skipped when it does not exist (a database where a single user owns everything).
DO $$
DECLARE
    app_role TEXT := '${appRole}';
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = app_role) THEN
        EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', app_role);
        EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I', app_role);
        EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO %I', app_role);
    END IF;
END
$$;
