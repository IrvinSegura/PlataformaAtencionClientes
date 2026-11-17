package com.irvinsegura.supportchat.channels.domain.port;

import com.irvinsegura.supportchat.channels.domain.model.DeliveryFailedException;
import com.irvinsegura.supportchat.channels.domain.model.MetaChannel;
import com.irvinsegura.supportchat.channels.domain.model.OutboundMessage;
import java.util.Optional;

/**
 * Meta's Graph API as the Page: the Send API, and the profiles of the people who write.
 */
public interface MetaGraph {

    /**
     * A text longer than the platform allows goes out as several messages, the buttons with the last one.
     *
     * @throws DeliveryFailedException.Temporary when sending may work later
     * @throws DeliveryFailedException.Permanent when Meta refused it for good, e.g. outside the 24-hour window
     */
    void send(MetaChannel channel, OutboundMessage message);

    /**
     * The name a customer shows on the platform, for the operators; empty when Meta does not give it.
     */
    Optional<String> contactName(MetaChannel channel, String contactId);
}
