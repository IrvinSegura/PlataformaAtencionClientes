/**
 * Actions the assistant can take in each business's own systems, such as looking up an order or changing a delivery
 * address. A workspace registers each action as a webhook of its own, with the parameters the language model must
 * fill in; the platform validates what the model asks for, calls the webhook with an HMAC-signed request and gives the
 * model the result. Actions that change something wait until the visitor confirms them.
 * <p>
 * The conversations module offers the actions to the model as tools through
 * {@link com.irvinsegura.supportchat.actions.AssistantActions}.
 */
@ApplicationModule(displayName = "Actions")
package com.irvinsegura.supportchat.actions;

import org.springframework.modulith.ApplicationModule;
