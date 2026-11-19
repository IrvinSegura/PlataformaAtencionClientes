// Follow-ups the team schedules for channel customers, and their templates (backend module followups)

import type { ConversationChannel } from './conversations';
import { pathSegment, request } from './http-client';
import type { Page } from './page';

export type FollowUpStatus = 'SCHEDULED' | 'SENT' | 'FAILED' | 'CANCELLED';

/** The approved WhatsApp template sent instead of the text after 24 hours without a message of the customer. */
export interface WhatsAppTemplate {
  name: string;
  language: string;
  /** Whether its {{1}} is the customer's name. */
  nameParameter: boolean;
}

export interface FollowUpTemplate {
  id: string;
  name: string;
  /** What the customer reads; {nombre} becomes their name. */
  body: string;
  whatsapp: WhatsAppTemplate | null;
  createdAt: string;
  updatedAt: string;
}

export interface FollowUpTemplateInput {
  name: string;
  body: string;
  whatsappTemplate: string | null;
  whatsappLanguage: string | null;
  whatsappNameParameter: boolean;
}

export interface FollowUp {
  id: string;
  conversationId: string;
  templateId: string | null;
  templateName: string | null;
  /** The text, with {nombre} still in place. */
  text: string;
  whatsappTemplate: string | null;
  dueAt: string;
  status: FollowUpStatus;
  /** Why it could not be sent: WINDOW_CLOSED, WINDOW_CLOSED_NO_TEMPLATE… */
  failure: string | null;
  createdByName: string;
  createdAt: string;
  finishedAt: string | null;
  sentConversationId: string | null;
  channel: ConversationChannel;
  contactId: string | null;
  contactName: string | null;
}

export interface FollowUpInput {
  templateId: string | null;
  text: string | null;
  dueAt: string;
}

export const followUpsApi = {
  templates: () => request<FollowUpTemplate[]>('/api/v1/follow-up-templates'),
  createTemplate: (input: FollowUpTemplateInput) =>
    request<FollowUpTemplate>('/api/v1/follow-up-templates', { json: input }),
  updateTemplate: (templateId: string, input: FollowUpTemplateInput) =>
    request<FollowUpTemplate>(`/api/v1/follow-up-templates/${pathSegment(templateId)}`, { method: 'PUT', json: input }),
  deleteTemplate: (templateId: string) =>
    request<void>(`/api/v1/follow-up-templates/${pathSegment(templateId)}`, { method: 'DELETE' }),
  list: (status: FollowUpStatus | null, page: number, size = 20) =>
    request<Page<FollowUp>>(`/api/v1/follow-ups?page=${page}&size=${size}${status ? `&status=${status}` : ''}`),
  ofConversation: (conversationId: string) =>
    request<FollowUp[]>(`/api/v1/conversations/${pathSegment(conversationId)}/follow-ups`),
  schedule: (conversationId: string, input: FollowUpInput) =>
    request<FollowUp>(`/api/v1/conversations/${pathSegment(conversationId)}/follow-ups`, { json: input }),
  cancel: (followUpId: string) =>
    request<FollowUp>(`/api/v1/follow-ups/${pathSegment(followUpId)}/cancellation`, { method: 'POST' }),
};
