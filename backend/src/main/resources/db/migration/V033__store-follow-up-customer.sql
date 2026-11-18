-- Who a follow-up is for, kept with the follow-up, so the follow-ups module lists them without reading the
-- conversations module's tables. Copied when scheduled, like the text.
ALTER TABLE follow_ups
    ADD COLUMN channel      TEXT,
    ADD COLUMN contact_id   TEXT,
    ADD COLUMN contact_name TEXT;

-- No workspace is bound while migrating, and FORCE applies row-level security to an owner that is not a superuser
-- (the deployments migrate as the container's superuser, which skips it): lift it for the copy, inside this
-- migration's transaction, and put it back
ALTER TABLE follow_ups NO FORCE ROW LEVEL SECURITY;
ALTER TABLE conversations NO FORCE ROW LEVEL SECURITY;

UPDATE follow_ups f
SET channel      = c.channel,
    contact_id   = c.contact_id,
    contact_name = c.contact_name
FROM conversations c
WHERE c.id = f.conversation_id;

ALTER TABLE conversations FORCE ROW LEVEL SECURITY;
ALTER TABLE follow_ups FORCE ROW LEVEL SECURITY;

ALTER TABLE follow_ups ALTER COLUMN channel SET NOT NULL;
