package com.irvinsegura.supportchat.actions.domain.port;

/**
 * Decides which addresses the platform may call on a business's behalf. Without it an operator could point an action
 * at the platform's own network (databases, cloud metadata) and read the answers through the assistant.
 */
public interface EndpointPolicy {

    /**
     * @throws com.irvinsegura.supportchat.actions.domain.model.EndpointNotAllowedException if the address may not be
     *         called
     */
    void check(String url);
}
