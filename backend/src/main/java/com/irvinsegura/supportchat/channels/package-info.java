/**
 * Messaging channels besides the web widget. A workspace connects its WhatsApp Business number (Cloud API): Meta
 * delivers customers' messages to a signed webhook, the conversations module answers them like widget questions, and
 * every reply is sent back through the Graph API, with buttons when the assistant asks to confirm an action.
 */
@ApplicationModule(displayName = "Channels")
package com.irvinsegura.supportchat.channels;

import org.springframework.modulith.ApplicationModule;
