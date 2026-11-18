package com.irvinsegura.supportchat.billing.domain.model;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

public class TopUpNotFoundException extends NotFoundException {

    public TopUpNotFoundException() {
        super("TOP_UP_NOT_FOUND", "There is no such recharge");
    }
}
