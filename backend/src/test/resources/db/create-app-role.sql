-- Runs once in the test container before Flyway. The application connects with this role, which owns no table,
-- so row-level security applies exactly as in production (the container's own user is a superuser).
CREATE ROLE supportchat_app LOGIN PASSWORD 'app-test-password';
