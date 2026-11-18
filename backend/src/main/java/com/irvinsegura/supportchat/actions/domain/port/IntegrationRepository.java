package com.irvinsegura.supportchat.actions.domain.port;

import com.irvinsegura.supportchat.actions.domain.model.Integration;
import com.irvinsegura.supportchat.actions.domain.model.IntegrationProvider;
import java.util.List;
import java.util.Optional;

/**
 * The integrations of the workspace bound to the current code. Implementations keep the tokens encrypted at rest.
 */
public interface IntegrationRepository {

    List<Integration> all();

    Optional<Integration> find(IntegrationProvider provider);

    void save(Integration integration);

    /**
     * @return false when it was not connected
     */
    boolean delete(IntegrationProvider provider);
}
