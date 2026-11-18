/**
 * Follow-ups: messages the team schedules to write later to a customer who talks from WhatsApp, email, Messenger or
 * Instagram, and the templates they write them from. When their moment comes they go out through the
 * {@link com.irvinsegura.supportchat.conversations.CustomerMessages} API of the conversations module, as a reply of
 * the member who scheduled them, in the customer's open conversation.
 */
@ApplicationModule(displayName = "Follow-ups")
package com.irvinsegura.supportchat.followups;

import org.springframework.modulith.ApplicationModule;
