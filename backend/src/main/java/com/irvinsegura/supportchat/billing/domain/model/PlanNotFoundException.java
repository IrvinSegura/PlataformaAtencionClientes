package com.irvinsegura.supportchat.billing.domain.model;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

public class PlanNotFoundException extends NotFoundException {

    public PlanNotFoundException() {
        super("PLAN_NOT_FOUND", "There is no such plan");
    }
}
