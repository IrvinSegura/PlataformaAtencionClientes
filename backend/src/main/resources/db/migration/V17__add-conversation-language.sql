-- The language the customer writes in, for the texts the platform words itself (fixed replies, notices). Starts from
-- the browser's preference and follows the customer's messages; null until either tells
ALTER TABLE conversations
    ADD COLUMN language TEXT,
    ADD CONSTRAINT conversations_language_check CHECK (language IN ('es', 'en', 'pt', 'fr'));
