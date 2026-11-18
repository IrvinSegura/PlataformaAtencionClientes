package com.irvinsegura.supportchat.conversations.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ChatTurn;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolCall;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolField;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolResult;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolSpec;
import com.irvinsegura.supportchat.conversations.domain.model.GroundedPrompt;
import com.irvinsegura.supportchat.conversations.domain.model.ModelTier;
import com.irvinsegura.supportchat.conversations.domain.port.ChatModel.Completion;
import com.irvinsegura.supportchat.conversations.infrastructure.llm.OfflineChatModel.ActionReplies;
import com.irvinsegura.supportchat.shared.domain.Language;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OfflineChatModelToolsTest {

    private static final ToolSpec ORDER_STATUS = new ToolSpec("order_status",
            "Consulta el estado de un pedido por su número",
            List.of(new ToolField("order_number", "string", "Número de pedido", true, "\\d{5,10}")));
    private static final ToolSpec CHANGE_ADDRESS = new ToolSpec("change_address",
            "Cambia la dirección de entrega de un pedido",
            List.of(new ToolField("order_number", "string", "Número de pedido", true, "\\d{5,10}"),
                    new ToolField("address", "string", "Nueva dirección", true, null)));

    private final OfflineChatModel model = new OfflineChatModel();

    @Test
    void callsTheActionTheQuestionAsksForWithTheValuesItContains() {
        Completion completion = model.stream(prompt("¿Cuál es el estado de mi pedido 123456?"), ModelTier.SIMPLE,
                text -> {
                });

        assertThat(completion.toolCalls()).containsExactly(
                new ToolCall("offline-order_status", "order_status", Map.of("order_number", "123456")));
    }

    @Test
    void doesNotCallAnActionWhenARequiredValueIsMissing() {
        assertThat(OfflineChatModel.toolCall(List.of(ORDER_STATUS), "¿Cuál es el estado de mi pedido?")).isEmpty();
        // The address cannot be found in the question, so the change is not proposed with a made-up one
        assertThat(OfflineChatModel.toolCall(List.of(CHANGE_ADDRESS), "Cambiar dirección del pedido 123456"))
                .isEmpty();
    }

    @Test
    void doesNotCallAnActionForAnUnrelatedQuestion() {
        assertThat(OfflineChatModel.toolCall(List.of(ORDER_STATUS), "¿Aceptan pagos con tarjeta 12345?")).isEmpty();
    }

    @Test
    void reportsWhatTheActionReturned() {
        StringBuilder text = new StringBuilder();

        model.stream(afterTool(new ToolResult("t1", "The business's system answered:\nEnviado ayer", false)),
                ModelTier.SIMPLE, text::append);

        assertThat(text).hasToString(ActionReplies.in(Language.ES).resultPrefix() + "Enviado ayer");
    }

    @Test
    void asksToConfirmAPendingAction() {
        StringBuilder text = new StringBuilder();

        model.stream(afterTool(new ToolResult("t1", "PENDING_CONFIRMATION: nothing has been done yet.", false)),
                ModelTier.SIMPLE, text::append);

        assertThat(text).hasToString(ActionReplies.in(Language.ES).pending());
    }

    @Test
    void speaksTheLanguageOfTheQuestionAfterAnAction() {
        StringBuilder pending = new StringBuilder();
        StringBuilder failed = new StringBuilder();

        model.stream(afterTool("What is the status of my order 123456?",
                new ToolResult("t1", "PENDING_CONFIRMATION: nothing has been done yet.", false)),
                ModelTier.SIMPLE, pending::append);
        model.stream(afterTool("Qual é o status do meu pedido 123456?", new ToolResult("t1", "timeout", true)),
                ModelTier.SIMPLE, failed::append);

        assertThat(pending).hasToString("To continue, confirm the action with the button.");
        assertThat(failed).hasToString("Desculpe, não consegui concluir a ação agora.");
    }

    private static ChatPrompt prompt(String question) {
        return GroundedPrompt.build("Ana", "Acme", List.of(), question, List.of(), List.of(ORDER_STATUS));
    }

    private static ChatPrompt afterTool(ToolResult result) {
        return afterTool("Estado del pedido 123456", result);
    }

    private static ChatPrompt afterTool(String question, ToolResult result) {
        ChatPrompt first = prompt(question);
        return new ChatPrompt(first.system(), List.of(
                first.turns().getFirst(),
                new ChatTurn(ChatTurn.Role.ASSISTANT, "", List.of(new ToolCall("t1", "order_status",
                        Map.of("order_number", "123456"))), List.of(), null),
                new ChatTurn(ChatTurn.Role.VISITOR, "", List.of(), List.of(result), null)),
                List.of(), first.tools());
    }
}
