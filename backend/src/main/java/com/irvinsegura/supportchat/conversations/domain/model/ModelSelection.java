package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.conversations.domain.model.ModelRouter.Routing;
import java.util.List;

/**
 * Strategy that decides which model tier answers a question. The {@link ModelRouter} decides per question; a
 * {@link Fixed} selection always answers with the tier a business chose for its channel. Either way the answer service
 * and the model adapters stay the same: only the strategy changes.
 */
public sealed interface ModelSelection permits ModelRouter, ModelSelection.Fixed {

    Routing select(String question, List<Source> sources);

    /**
     * Whether a "no answer" from the fast tier may be retried on the capable one. Only when the platform chose the
     * tier: a business that picked a model gets that model.
     */
    boolean mayEscalate();

    static ModelSelection fixed(ModelTier tier) {
        return new Fixed(tier);
    }

    /**
     * Always the same tier.
     */
    record Fixed(ModelTier tier) implements ModelSelection {

        public static final String CHOSEN_FAST = "chosen-fast";
        public static final String CHOSEN_PRECISE = "chosen-precise";

        @Override
        public Routing select(String question, List<Source> sources) {
            return new Routing(tier, tier == ModelTier.SIMPLE ? CHOSEN_FAST : CHOSEN_PRECISE);
        }

        @Override
        public boolean mayEscalate() {
            return false;
        }
    }
}
