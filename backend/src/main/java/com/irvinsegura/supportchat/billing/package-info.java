/**
 * Usage of each workspace, measured so it can be billed: answers, language model tokens per model, actions run and
 * WhatsApp messages, added up per day. Other modules record what they consume through
 * {@link com.irvinsegura.supportchat.billing.UsageMeter}, inside the transaction of the work they measure; owners and
 * administrators see each month's usage and its estimated cost.
 */
@ApplicationModule(displayName = "Billing")
package com.irvinsegura.supportchat.billing;

import org.springframework.modulith.ApplicationModule;
