package com.irvinsegura.supportchat.actions.domain.port;

import com.irvinsegura.supportchat.actions.domain.model.Action;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Actions of the workspace bound to the current transaction.
 */
public interface ActionRepository {

    /**
     * @throws com.irvinsegura.supportchat.actions.domain.model.DuplicateActionNameException if the name is taken
     */
    void insert(Action action);

    /**
     * @throws com.irvinsegura.supportchat.actions.domain.model.DuplicateActionNameException if the name is taken
     */
    void update(Action action);

    boolean delete(UUID id);

    Optional<Action> findById(UUID id);

    /**
     * Ordered by name.
     */
    List<Action> all();

    long count();
}
