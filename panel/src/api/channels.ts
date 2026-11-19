// WhatsApp, email, Messenger and Instagram channels (backend module channels)

import type { ModelPreference } from './conversations';
import { request } from './http-client';

export interface WhatsAppChannel {
  phoneNumberId: string;
  displayPhoneNumber: string | null;
  enabled: boolean;
  webhookPath: string;
  verifyToken: string;
  model?: ModelPreference;
  updatedAt: string;
}

/** Secrets are write-only: blank keeps the saved ones. */
export interface WhatsAppChannelInput {
  phoneNumberId: string;
  displayPhoneNumber: string;
  accessToken: string;
  appSecret: string;
  enabled: boolean;
  model: ModelPreference;
}

/** The workspace's support mailbox; the password is never sent back. */
export interface EmailChannel {
  address: string;
  displayName: string | null;
  imapHost: string;
  imapPort: number;
  smtpHost: string;
  smtpPort: number;
  username: string;
  enabled: boolean;
  /** When the mailbox was last read; null before the first read. */
  checkedAt: string | null;
  /** Why the last read failed; null when it worked. */
  lastError: string | null;
  updatedAt: string;
}

/** A blank password keeps the saved one; a blank username uses the address. */
export interface EmailChannelInput {
  address: string;
  displayName: string;
  imapHost: string;
  imapPort: number;
  smtpHost: string;
  smtpPort: number;
  username: string;
  password: string;
  enabled: boolean;
}

export type MetaPlatform = 'MESSENGER' | 'INSTAGRAM';

/** A Facebook Page (Messenger) or an Instagram professional account. */
export interface MetaChannel {
  platform: MetaPlatform;
  /** The Page ID or the Instagram account ID. */
  accountId: string;
  displayName: string | null;
  enabled: boolean;
  webhookPath: string;
  verifyToken: string;
  updatedAt: string;
}

/** Secrets are write-only: blank keeps the saved ones. */
export interface MetaChannelInput {
  accountId: string;
  displayName: string;
  accessToken: string;
  appSecret: string;
  enabled: boolean;
}

export const channelsApi = {
  whatsApp: () => request<WhatsAppChannel>('/api/v1/channels/whatsapp'),
  saveWhatsApp: (input: WhatsAppChannelInput) =>
    request<WhatsAppChannel>('/api/v1/channels/whatsapp', { method: 'PUT', json: input }),
  disconnectWhatsApp: () => request<void>('/api/v1/channels/whatsapp', { method: 'DELETE' }),
  email: () => request<EmailChannel>('/api/v1/channels/email'),
  saveEmail: (input: EmailChannelInput) =>
    request<EmailChannel>('/api/v1/channels/email', { method: 'PUT', json: input }),
  disconnectEmail: () => request<void>('/api/v1/channels/email', { method: 'DELETE' }),
  meta: (platform: MetaPlatform) => request<MetaChannel>(`/api/v1/channels/${metaPath(platform)}`),
  saveMeta: (platform: MetaPlatform, input: MetaChannelInput) =>
    request<MetaChannel>(`/api/v1/channels/${metaPath(platform)}`, { method: 'PUT', json: input }),
  disconnectMeta: (platform: MetaPlatform) =>
    request<void>(`/api/v1/channels/${metaPath(platform)}`, { method: 'DELETE' }),
};

function metaPath(platform: MetaPlatform): string {
  return platform === 'MESSENGER' ? 'messenger' : 'instagram';
}
