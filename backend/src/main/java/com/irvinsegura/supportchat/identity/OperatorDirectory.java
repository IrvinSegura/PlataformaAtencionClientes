package com.irvinsegura.supportchat.identity;

import java.util.Optional;
import java.util.UUID;

/**
 * Public facts about operators that other modules may show, such as the name a visitor sees when an agent joins.
 */
public interface OperatorDirectory {

    /**
     * @throws com.irvinsegura.supportchat.shared.domain.UnauthenticatedException if the account no longer exists
     */
    String displayNameOf(UUID userId);

    /**
     * The address an operator signs in with, for those who run the platform to reach a workspace's owner; empty if
     * the account no longer exists.
     */
    Optional<String> emailOf(UUID userId);
}
