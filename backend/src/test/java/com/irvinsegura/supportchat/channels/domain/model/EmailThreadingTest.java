package com.irvinsegura.supportchat.channels.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class EmailThreadingTest {

    private static final UUID RUN = UUID.fromString("0b7c43c4-5f0e-4a9b-9f3e-2d1c0a9b8e7f");
    private static final String PROPOSAL = EmailActionReply.messageIdFor(RUN, "n0nce", "tienda.mx");

    @Test
    void aProposalsIdCarriesItsRun() {
        assertThat(PROPOSAL).isEqualTo("<action." + RUN + ".n0nce@tienda.mx>");
    }

    @Test
    void aShortAnswerToAProposalConfirmsOrCancelsIt() {
        assertThat(EmailActionReply.read(reply("CONFIRMAR\n\nEl mar, 10 nov escribió:", PROPOSAL, List.of())))
                .contains(new EmailActionReply(RUN, true));
        assertThat(EmailActionReply.read(reply("Sí, confirmo", PROPOSAL, List.of())))
                .contains(new EmailActionReply(RUN, true));
        assertThat(EmailActionReply.read(reply("no.", PROPOSAL, List.of())))
                .contains(new EmailActionReply(RUN, false));
        assertThat(EmailActionReply.read(reply("Cancel please", null, List.of("<a@x>", PROPOSAL))))
                .contains(new EmailActionReply(RUN, false));
    }

    @Test
    void anythingElseIsNotAnAnswer() {
        assertThat(EmailActionReply.read(reply("No entiendo qué hace", PROPOSAL, List.of()))).isEmpty();
        assertThat(EmailActionReply.read(reply("Tal vez", PROPOSAL, List.of()))).isEmpty();
        assertThat(EmailActionReply.read(reply("", PROPOSAL, List.of()))).isEmpty();
        // Confirming a message that proposed nothing confirms nothing
        assertThat(EmailActionReply.read(reply("Confirmar", "<reply.1@tienda.mx>", List.of()))).isEmpty();
    }

    @Test
    void theNewestProposalInTheThreadIsTheOneAnswered() {
        UUID older = UUID.randomUUID();
        String olderProposal = EmailActionReply.messageIdFor(older, "a", "tienda.mx");

        assertThat(EmailActionReply.read(reply("Sí", "<customer.2@gmail.com>",
                List.of(olderProposal, PROPOSAL, "<customer.2@gmail.com>"))))
                .map(EmailActionReply::runId).contains(RUN);
    }

    @Test
    void aThreadKeepsItsNewestIdsAndTheBaseSubject() {
        List<String> earlier = IntStream.range(0, 12).mapToObj(i -> "<m" + i + "@x>").toList();
        InboundEmail email = new InboundEmail("<last@x>", "ana@example.com", "Ana", "RE: Fwd: re:  Mi pedido",
                "Hola", "<m11@x>", earlier, false, false);

        EmailThread thread = EmailThread.of(email);

        assertThat(thread.subject()).isEqualTo("Mi pedido");
        assertThat(thread.lastMessageId()).isEqualTo("<last@x>");
        assertThat(thread.references()).hasSize(EmailThread.MAX_REFERENCES).startsWith("<m3@x>")
                .endsWith("<m11@x>", "<last@x>");
        assertThat(thread.replySubject("Tu consulta")).isEqualTo("Re: Mi pedido");
    }

    @Test
    void aMailWithoutSubjectIsAnsweredWithTheFallback() {
        EmailThread thread = EmailThread.of(new InboundEmail("<1@x>", "ana@example.com", null, "Re: ", "Hola",
                "<0@x>", List.of(), false, false));

        assertThat(thread.subject()).isNull();
        assertThat(thread.references()).containsExactly("<0@x>", "<1@x>");
        assertThat(thread.replySubject("Tu consulta")).isEqualTo("Re: Tu consulta");
        assertThat(EmailThread.baseSubject(null)).isNull();
        assertThat(Optional.ofNullable(EmailThread.baseSubject("x".repeat(300)))).map(String::length).contains(200);
    }

    private static InboundEmail reply(String text, String inReplyTo, List<String> references) {
        return new InboundEmail("<customer." + UUID.randomUUID() + "@gmail.com>", "ana@example.com", "Ana",
                "Re: Tu pedido", text, inReplyTo, references, false, false);
    }
}
