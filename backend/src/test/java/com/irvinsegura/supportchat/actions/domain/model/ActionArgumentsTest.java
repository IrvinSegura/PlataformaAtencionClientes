package com.irvinsegura.supportchat.actions.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.supportchat.actions.ActionParameter;
import com.irvinsegura.supportchat.actions.ParameterType;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActionArgumentsTest {

    private static final List<ActionParameter> PARAMETERS = List.of(
            new ActionParameter("order_number", ParameterType.STRING, "Order number", true, "\\d{5,10}"),
            new ActionParameter("quantity", ParameterType.NUMBER, "How many", false, null),
            new ActionParameter("gift", ParameterType.BOOLEAN, "Gift wrap", false, null));

    @Test
    void keepsValidArgumentsInTheOrderOfTheParameters() {
        Map<String, Object> given = new LinkedHashMap<>();
        given.put("gift", true);
        given.put("quantity", 2);
        given.put("order_number", " 123456 ");

        assertThat(ActionArguments.validate(PARAMETERS, given)).containsExactly(
                Map.entry("order_number", "123456"),
                Map.entry("quantity", new BigDecimal("2")),
                Map.entry("gift", true));
    }

    @Test
    void reportsEveryProblemSoTheModelCanCorrectItself() {
        assertThatThrownBy(() -> ActionArguments.validate(PARAMETERS,
                Map.of("order_number", "12", "quantity", "many", "email", "a@b.c")))
                .isInstanceOf(InvalidActionArgumentsException.class)
                .hasMessageContaining("unknown parameter email")
                .hasMessageContaining("order_number does not have the expected format")
                .hasMessageContaining("quantity must be a number");
    }

    @Test
    void aMissingRequiredValueMustBeAskedForNotGuessed() {
        assertThatThrownBy(() -> ActionArguments.validate(PARAMETERS, Map.of("order_number", " ")))
                .hasMessageContaining("order_number is required; ask the customer for it");
    }
}
