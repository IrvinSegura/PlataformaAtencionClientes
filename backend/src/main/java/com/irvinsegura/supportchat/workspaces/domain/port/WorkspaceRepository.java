package com.irvinsegura.supportchat.workspaces.domain.port;

import com.irvinsegura.supportchat.workspaces.domain.model.WidgetKey;
import com.irvinsegura.supportchat.workspaces.domain.model.Workspace;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository {

    void insert(Workspace workspace);

    void update(Workspace workspace);

    Optional<Workspace> findById(UUID id);

    Optional<Workspace> findByWidgetKey(WidgetKey widgetKey);

    /**
     * Drops anything cached for this key, after it was rotated away or its workspace's settings changed.
     */
    void evict(WidgetKey widgetKey);
}
