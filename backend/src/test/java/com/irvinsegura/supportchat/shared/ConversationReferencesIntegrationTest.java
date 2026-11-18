package com.irvinsegura.supportchat.shared;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.ApiClient.Visitor;
import com.irvinsegura.supportchat.support.WithExternalServices;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Foreign keys are checked without row-level security, so the database itself has to refuse a row of one workspace
 * that points at a conversation of another.
 */
class ConversationReferencesIntegrationTest extends WithExternalServices {

    private static final String SITE = "https://shop.example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void aRowCannotPointAtAConversationOfAnotherWorkspace() throws Exception {
        ApiClient api = new ApiClient(mockMvc);
        Operator owner = api.newOperator("Acme");
        Visitor visitor = api.startConversation(api.enableWidget(owner, SITE), SITE);
        Operator stranger = api.newOperator("Otra");

        assertThatThrownBy(() -> recordActionRun(stranger.workspaceId(), visitor.conversationId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> recordActionRun(owner.workspaceId(), visitor.conversationId()))
                .doesNotThrowAnyException();
    }

    private void recordActionRun(UUID workspaceId, UUID conversationId) {
        TenantContext.runAs(workspaceId, () -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> jdbc.sql("""
                        INSERT INTO action_runs (id, workspace_id, action_name, action_title, conversation_id,
                                                 message_id, arguments, status, created_at)
                        VALUES (:id, current_workspace_id(), 'track_order', 'Rastrear pedido', :conversationId,
                                :messageId, '{}'::jsonb, 'SUCCEEDED', :now)
                        """)
                        .param("id", UUID.randomUUID())
                        .param("conversationId", conversationId)
                        .param("messageId", UUID.randomUUID())
                        .param("now", Timestamp.from(Instant.now()))
                        .update()));
    }
}
