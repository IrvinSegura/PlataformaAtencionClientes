-- The Claude model that answers a WhatsApp number's customers, as the business chose it on the panel.
-- AUTOMATIC lets the platform pick per question, so existing numbers keep answering as before.

ALTER TABLE whatsapp_channels
    ADD COLUMN model TEXT NOT NULL DEFAULT 'AUTOMATIC',
    ADD CONSTRAINT whatsapp_channels_model_check CHECK (model IN ('AUTOMATIC', 'FAST', 'PRECISE'));
