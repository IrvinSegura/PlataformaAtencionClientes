package com.irvinsegura.supportchat.channels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.irvinsegura.supportchat.channels.application.EmailChannelService;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.Eventually;
import com.irvinsegura.supportchat.support.ExternalServices;
import com.irvinsegura.supportchat.support.ExternalServices.Response;
import com.irvinsegura.supportchat.support.WithExternalServices;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * A customer writes to the business's support mailbox: the platform reads it over IMAP, the assistant answers from
 * the knowledge base and the reply goes out over SMTP in the customer's thread. GreenMail plays both mail servers.
 */
class EmailChannelIntegrationTest extends WithExternalServices {

    private static final String PASSWORD = "abcd efgh ijkl mnop";
    private static final String SHIPPING = """
            # Envíos

            El envío nacional tarda de 3 a 5 días hábiles y es gratis en compras mayores a 999 pesos.
            """;
    private static final GreenMail MAIL = startMailServers();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmailChannelService emailChannels;

    private ApiClient api;
    private Operator operator;
    // Each test gets its own mailbox and customer, so the mail it waits for is its own
    private String mailbox;
    private String customer;
    private GreenMailUser support;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc);
        operator = api.newOperator("Acme");
        String id = UUID.randomUUID().toString().substring(0, 8);
        mailbox = "soporte-" + id + "@tienda.test";
        customer = "cliente-" + id + "@cliente.test";
        support = MAIL.setUser(mailbox, mailbox, PASSWORD);
        connect(PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value(mailbox))
                .andExpect(jsonPath("$.username").value(mailbox))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.checkedAt").doesNotExist());
        // The first read only marks where the mailbox starts: mail that came before is not answered
        emailChannels.readAll();
        api.getAs(operator, "/api/v1/channels/email").andExpect(jsonPath("$.checkedAt").isNotEmpty())
                .andExpect(jsonPath("$.lastError").doesNotExist());
    }

    @AfterAll
    static void stopMailServers() {
        MAIL.stop();
    }

    @Test
    void aCustomersMailIsAnsweredInItsThread() throws Exception {
        api.uploadAndWaitUntilReady(operator, "envios.md", SHIPPING);
        String original = "<pregunta." + UUID.randomUUID() + "@cliente.test>";

        deliver(original, "Envío nacional", "¿Cuánto tarda el envío nacional?", "");
        emailChannels.readAll();

        MimeMessage reply = awaitReply(text -> text.contains("3 a 5 días hábiles"));
        assertThat(reply.getSubject()).isEqualTo("Re: Envío nacional");
        assertThat(reply.getHeader("In-Reply-To", null)).isEqualTo(original);
        assertThat(reply.getHeader("References", null)).contains(original);
        assertThat(reply.getHeader("Auto-Submitted", null)).isEqualTo("auto-replied");
        assertThat(((InternetAddress) reply.getFrom()[0]).getAddress()).isEqualTo(mailbox);
        assertThat(((InternetAddress) reply.getFrom()[0]).getPersonal()).isEqualTo("Soporte Acme");

        String conversations = api.getAs(operator, "/api/v1/conversations")
                .andExpect(jsonPath("$.total").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(conversations, "$.items[0].channel")).isEqualTo("EMAIL");
        assertThat((String) JsonPath.read(conversations, "$.items[0].contactId")).isEqualTo(customer);
        assertThat((String) JsonPath.read(conversations, "$.items[0].contactName")).isEqualTo("Lucía Pérez");
    }

    @Test
    void automaticMailAndMailReadTwiceAreNotAnswered() throws Exception {
        deliver("<auto." + UUID.randomUUID() + "@cliente.test>", "Fuera de la oficina",
                "Estoy de vacaciones hasta el lunes", "Auto-Submitted: auto-replied\n");
        String question = "<pregunta." + UUID.randomUUID() + "@cliente.test>";
        deliver(question, "Pregunta", "Quiero hablar con una persona", "");

        emailChannels.readAll();
        // A server that hands the same message out again under a new UID
        deliver(question, "Pregunta", "Quiero hablar con una persona", "");
        emailChannels.readAll();

        awaitReply(text -> text.contains("Te comunicaremos con una persona"));
        api.getAs(operator, "/api/v1/conversations").andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].questionCount").value(1));
        assertThat(repliesTo(customer)).hasSize(1);
    }

    @Test
    void aMailWithCharactersTheDatabaseRefusesIsStillAnswered() throws Exception {
        // PostgreSQL refuses NUL in text: storing it as it came would fail the read of the whole mailbox, every minute
        deliver("<nul." + UUID.randomUUID() + "@cliente.test>", "Pregunta\0", "Quiero hablar\0 con una persona", "");

        emailChannels.readAll();

        awaitReply(text -> text.contains("Te comunicaremos con una persona"));
        api.getAs(operator, "/api/v1/conversations").andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void aCustomerConfirmsAnActionByReplyingConfirmar() throws Exception {
        ExternalServices.on("POST", "/business/*",
                request -> Response.json(200, "{\"message\":\"Tu pedido 123456 fue cancelado.\"}"));
        api.postJsonAs(operator, "/api/v1/actions", ApiClient.json("""
                        {'name':'cancel_order','title':'Cancelar un pedido',
                         'description':'Cancela un pedido por su número','endpointUrl':'%s/business/cancel_order',
                         'parameters':[{'name':'order_number','type':'STRING','description':'Número de pedido',
                                        'required':true,'pattern':'\\\\d{5,10}'}],
                         'requiresConfirmation':true,'enabled':true}""", ExternalServices.baseUrl()))
                .andExpect(status().isCreated());
        String original = "<cancelar." + UUID.randomUUID() + "@cliente.test>";
        deliver(original, "Quiero cancelar mi pedido 123456", "Quiero cancelar mi pedido 123456", "");
        emailChannels.readAll();

        MimeMessage proposal = awaitReply(text -> text.contains("CONFIRMAR"));
        assertThat(proposal.getMessageID()).startsWith("<action.");
        assertThat(ExternalServices.requests(sent -> sent.path().equals("/business/cancel_order"))).isEmpty();

        deliver("<respuesta." + UUID.randomUUID() + "@cliente.test>", "Re: Quiero cancelar mi pedido 123456",
                "CONFIRMAR\n\nEl mar, 10 nov 2026 a las 9:00, Soporte Acme <" + mailbox + "> escribió:\n> ...",
                "In-Reply-To: " + proposal.getMessageID() + "\nReferences: " + original + " "
                        + proposal.getMessageID() + "\n");
        emailChannels.readAll();

        awaitReply(text -> text.contains("Tu pedido 123456 fue cancelado."));
        assertThat(ExternalServices.requests(sent -> sent.path().equals("/business/cancel_order"))).hasSize(1);
    }

    @Test
    void aMailboxThatCannotBeReadShowsWhy() throws Exception {
        connect("not-the-password").andExpect(status().isOk());

        emailChannels.readAll();

        api.getAs(operator, "/api/v1/channels/email")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastError").value(containsString("username or password")));
    }

    @Test
    void onlyOwnersAndAdministratorsSeeTheMailbox() throws Exception {
        Operator agent = api.newMember(operator, "AGENT");

        api.getAs(agent, "/api/v1/channels/email").andExpect(status().isForbidden());
        api.putJsonAs(operator, "/api/v1/channels/email", ApiClient.json(
                        "{'address':'not-an-address','imapHost':'127.0.0.1','imapPort':993,'smtpHost':'127.0.0.1',"
                                + "'smtpPort':465,'password':'','enabled':true}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL_CHANNEL"));
        api.deleteAs(operator, "/api/v1/channels/email").andExpect(status().isNoContent());
        api.getAs(operator, "/api/v1/channels/email").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EMAIL_CHANNEL_NOT_FOUND"));
    }

    private ResultActions connect(String password) throws Exception {
        return api.putJsonAs(operator, "/api/v1/channels/email", ApiClient.json(
                "{'address':'%s','displayName':'Soporte Acme','imapHost':'127.0.0.1','imapPort':%d,"
                        + "'smtpHost':'127.0.0.1','smtpPort':%d,'username':'','password':'%s','enabled':true}",
                mailbox, MAIL.getImap().getPort(), MAIL.getSmtp().getPort(), password));
    }

    /**
     * Puts a customer's mail straight into the support mailbox.
     *
     * @param headers extra header lines, each ending in a line break
     */
    private void deliver(String messageId, String subject, String text, String headers) throws MessagingException {
        String raw = headers + """
                From: =?UTF-8?Q?Luc=C3=ADa_P=C3=A9rez?= <%s>
                To: %s
                Subject: =?UTF-8?B?%s?=
                Message-ID: %s
                Date: Tue, 10 Nov 2026 09:00:00 -0600
                MIME-Version: 1.0
                Content-Type: text/plain; charset=UTF-8
                Content-Transfer-Encoding: 8bit

                %s
                """.formatted(customer, mailbox,
                Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)), messageId,
                text);
        support.deliver(new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(raw.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8))));
    }

    private MimeMessage awaitReply(Predicate<String> matches) throws Exception {
        MimeMessage[] found = new MimeMessage[1];
        Eventually.assertEventually(Duration.ofSeconds(20), () -> {
            for (MimeMessage message : repliesTo(customer)) {
                if (matches.test(text(message))) {
                    found[0] = message;
                    return;
                }
            }
            throw new AssertionError("No such reply yet");
        });
        return found[0];
    }

    private static List<MimeMessage> repliesTo(String address) throws MessagingException {
        List<MimeMessage> replies = new ArrayList<>();
        for (MimeMessage message : MAIL.getReceivedMessages()) {
            if (Arrays.stream(message.getRecipients(Message.RecipientType.TO))
                    .anyMatch(to -> ((InternetAddress) to).getAddress().equalsIgnoreCase(address))) {
                replies.add(message);
            }
        }
        return replies;
    }

    private static String text(MimeMessage message) {
        try {
            return message.getContent() instanceof String text ? text : "";
        } catch (IOException | MessagingException e) {
            return "";
        }
    }

    private static GreenMail startMailServers() {
        GreenMail mail = new GreenMail(ServerSetup.dynamicPort(ServerSetupTest.SMTP_IMAP));
        mail.start();
        return mail;
    }
}
