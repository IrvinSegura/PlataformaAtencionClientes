package com.irvinsegura.supportchat.workspaces.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

/**
 * Every workspace has exactly one owner, the person who created it: nobody can become owner, and the owner can be
 * neither demoted nor removed.
 */
public class OwnerIsPermanentException extends DomainException {

    public OwnerIsPermanentException() {
        super("OWNER_IS_PERMANENT", "A workspace keeps its one owner: the role cannot be given, changed or removed");
    }
}
