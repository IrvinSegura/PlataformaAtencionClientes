package com.irvinsegura.supportchat.channels.domain.port;

import com.irvinsegura.supportchat.channels.domain.model.MetaChannel;
import com.irvinsegura.supportchat.channels.domain.model.MetaPlatform;
import java.util.Optional;
import java.util.UUID;

/**
 * The Messenger and Instagram accounts of the workspace bound to the current transaction.
 */
public interface MetaChannelRepository {

    Optional<MetaChannel> find(MetaPlatform platform);

    void save(MetaChannel channel);

    void delete(UUID workspaceId, MetaPlatform platform);

    /**
     * Which workspace and platform a webhook URL belongs to, before any workspace is bound.
     */
    Optional<KeyOwner> ownerOfKey(String channelKey);

    record KeyOwner(UUID workspaceId, MetaPlatform platform) {
    }
}
