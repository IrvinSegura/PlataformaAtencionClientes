package com.irvinsegura.supportchat.actions.domain.model;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

/**
 * The workspace has not connected that integration.
 */
public class IntegrationNotFoundException extends NotFoundException {

    public IntegrationNotFoundException() {
        super("INTEGRATION_NOT_FOUND", "The integration is not connected");
    }
}
