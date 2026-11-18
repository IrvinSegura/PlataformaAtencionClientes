package com.irvinsegura.supportchat.followups.infrastructure.persistence;

import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.followups.domain.model.FollowUp;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpStatus;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpTemplate;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpTemplate.WhatsAppTemplate;
import com.irvinsegura.supportchat.followups.domain.port.FollowUpRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Filtered by row-level security, except the queue the job reads before any workspace is bound.
 */
@Repository
class JdbcFollowUpRepository implements FollowUpRepository {

    private static final String TEMPLATE_COLUMNS = """
            id, name, body, whatsapp_template, whatsapp_language, whatsapp_name_parameter, created_at, updated_at""";

    private static final String COLUMNS = """
            f.id, f.conversation_id, f.template_id, f.body, f.whatsapp_template, f.whatsapp_language,
            f.whatsapp_name_parameter, f.due_at, f.status, f.failure, f.created_by, f.created_by_name, f.created_at,
            f.finished_at, f.sent_conversation_id, f.message_id""";

    private static final String ROWS = "SELECT " + COLUMNS + """
            , t.name AS template_name, f.channel, f.contact_id, f.contact_name
             FROM follow_ups f
             LEFT JOIN follow_up_templates t ON t.id = f.template_id
            """;

    private final JdbcClient jdbc;

    JdbcFollowUpRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowUpTemplate> templates() {
        return jdbc.sql("SELECT " + TEMPLATE_COLUMNS + " FROM follow_up_templates ORDER BY lower(name), id")
                .query(JdbcFollowUpRepository::template)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FollowUpTemplate> findTemplate(UUID id) {
        return jdbc.sql("SELECT " + TEMPLATE_COLUMNS + " FROM follow_up_templates WHERE id = :id")
                .param("id", id)
                .query(JdbcFollowUpRepository::template)
                .optional();
    }

    @Override
    @Transactional
    public boolean saveTemplate(FollowUpTemplate template) {
        // The name is checked here and not by catching the unique violation, which would spoil the transaction
        boolean taken = jdbc.sql("SELECT EXISTS (SELECT 1 FROM follow_up_templates WHERE name = :name AND id <> :id)")
                .param("name", template.name())
                .param("id", template.id())
                .query(Boolean.class)
                .single();
        if (taken) {
            return false;
        }
        WhatsAppTemplate whatsapp = template.whatsapp();
        jdbc.sql("""
                        INSERT INTO follow_up_templates (id, workspace_id, name, body, whatsapp_template,
                                                         whatsapp_language, whatsapp_name_parameter, created_at,
                                                         updated_at)
                        VALUES (:id, current_workspace_id(), :name, :body, :whatsappTemplate, :whatsappLanguage,
                                :nameParameter, :createdAt, :updatedAt)
                        ON CONFLICT (id) DO UPDATE
                        SET name = EXCLUDED.name, body = EXCLUDED.body,
                            whatsapp_template = EXCLUDED.whatsapp_template,
                            whatsapp_language = EXCLUDED.whatsapp_language,
                            whatsapp_name_parameter = EXCLUDED.whatsapp_name_parameter,
                            updated_at = EXCLUDED.updated_at
                        """)
                .param("id", template.id())
                .param("name", template.name())
                .param("body", template.body())
                .param("whatsappTemplate", whatsapp == null ? null : whatsapp.name())
                .param("whatsappLanguage", whatsapp == null ? null : whatsapp.language())
                .param("nameParameter", whatsapp != null && whatsapp.nameParameter())
                .param("createdAt", Timestamp.from(template.createdAt()))
                .param("updatedAt", Timestamp.from(template.updatedAt()))
                .update();
        return true;
    }

    @Override
    @Transactional
    public boolean deleteTemplate(UUID id) {
        return jdbc.sql("DELETE FROM follow_up_templates WHERE id = :id").param("id", id).update() == 1;
    }

    @Override
    @Transactional
    public void insert(FollowUpRow row) {
        FollowUp followUp = row.followUp();
        WhatsAppTemplate whatsapp = followUp.whatsapp();
        jdbc.sql("""
                        INSERT INTO follow_ups (id, workspace_id, conversation_id, channel, contact_id, contact_name,
                                                template_id, body, whatsapp_template, whatsapp_language,
                                                whatsapp_name_parameter, due_at, status, created_by,
                                                created_by_name, created_at)
                        VALUES (:id, current_workspace_id(), :conversationId, :channel, :contactId, :contactName,
                                :templateId, :body, :whatsappTemplate, :whatsappLanguage, :nameParameter, :dueAt,
                                :status, :createdBy, :createdByName, :createdAt)
                        """)
                .param("id", followUp.id())
                .param("conversationId", followUp.conversationId())
                .param("channel", row.channel().name())
                .param("contactId", row.contactId())
                .param("contactName", row.contactName())
                .param("templateId", followUp.templateId())
                .param("body", followUp.body())
                .param("whatsappTemplate", whatsapp == null ? null : whatsapp.name())
                .param("whatsappLanguage", whatsapp == null ? null : whatsapp.language())
                .param("nameParameter", whatsapp != null && whatsapp.nameParameter())
                .param("dueAt", Timestamp.from(followUp.dueAt()))
                .param("status", followUp.status().name())
                .param("createdBy", followUp.createdBy())
                .param("createdByName", followUp.createdByName())
                .param("createdAt", Timestamp.from(followUp.createdAt()))
                .update();
        jdbc.sql("""
                        INSERT INTO follow_up_queue (follow_up_id, workspace_id, due_at)
                        VALUES (:id, current_workspace_id(), :dueAt)
                        """)
                .param("id", followUp.id())
                .param("dueAt", Timestamp.from(followUp.dueAt()))
                .update();
    }

    @Override
    @Transactional
    public void update(FollowUp followUp) {
        jdbc.sql("""
                        UPDATE follow_ups
                        SET status = :status, failure = :failure, finished_at = :finishedAt,
                            sent_conversation_id = :sentConversationId, message_id = :messageId
                        WHERE id = :id
                        """)
                .param("status", followUp.status().name())
                .param("failure", followUp.failure())
                .param("finishedAt", followUp.finishedAt() == null ? null : Timestamp.from(followUp.finishedAt()))
                .param("sentConversationId", followUp.sentConversationId())
                .param("messageId", followUp.messageId())
                .param("id", followUp.id())
                .update();
        if (followUp.status() != FollowUpStatus.SCHEDULED) {
            jdbc.sql("DELETE FROM follow_up_queue WHERE follow_up_id = :id").param("id", followUp.id()).update();
        }
    }

    @Override
    @Transactional
    public Optional<FollowUp> findForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM follow_ups f WHERE f.id = :id FOR UPDATE")
                .param("id", id)
                .query(JdbcFollowUpRepository::followUp)
                .optional();
    }

    @Override
    @Transactional
    public Optional<FollowUp> findByMessageForUpdate(UUID messageId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM follow_ups f WHERE f.message_id = :messageId FOR UPDATE")
                .param("messageId", messageId)
                .query(JdbcFollowUpRepository::followUp)
                .optional();
    }

    @Override
    @Transactional
    public Optional<FollowUp> claimDue(Instant now) {
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM follow_ups f
                        WHERE f.status = 'SCHEDULED' AND f.due_at <= :now
                        ORDER BY f.due_at, f.id
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED
                        """)
                .param("now", Timestamp.from(now))
                .query(JdbcFollowUpRepository::followUp)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> workspacesWithDue(Instant now) {
        return jdbc.sql("SELECT DISTINCT workspace_id FROM follow_up_queue WHERE due_at <= :now ORDER BY workspace_id")
                .param("now", Timestamp.from(now))
                .query(UUID.class)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowUpRow> ofConversation(UUID conversationId) {
        return jdbc.sql(ROWS + " WHERE f.conversation_id = :conversationId ORDER BY f.due_at, f.id")
                .param("conversationId", conversationId)
                .query(JdbcFollowUpRepository::row)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public FollowUpPage page(FollowUpStatus status, int page, int size) {
        String where = status == null ? "" : " WHERE f.status = :status";
        String order = status == FollowUpStatus.SCHEDULED
                ? " ORDER BY f.due_at, f.id"
                : " ORDER BY coalesce(f.finished_at, f.due_at) DESC, f.id DESC";
        var rows = jdbc.sql(ROWS + where + order + " LIMIT :limit OFFSET :offset")
                .param("limit", size)
                .param("offset", (long) page * size);
        var count = jdbc.sql("SELECT count(*) FROM follow_ups f" + where);
        if (status != null) {
            rows = rows.param("status", status.name());
            count = count.param("status", status.name());
        }
        return new FollowUpPage(rows.query(JdbcFollowUpRepository::row).list(), count.query(Long.class).single());
    }

    private static FollowUpRow row(ResultSet rs, int row) throws SQLException {
        return new FollowUpRow(followUp(rs, row), rs.getString("template_name"),
                ConversationChannel.valueOf(rs.getString("channel")), rs.getString("contact_id"),
                rs.getString("contact_name"));
    }

    private static FollowUp followUp(ResultSet rs, int row) throws SQLException {
        return FollowUp.restore(
                rs.getObject("id", UUID.class),
                rs.getObject("conversation_id", UUID.class),
                rs.getObject("template_id", UUID.class),
                rs.getString("body"),
                whatsapp(rs),
                rs.getTimestamp("due_at").toInstant(),
                FollowUpStatus.valueOf(rs.getString("status")),
                rs.getString("failure"),
                rs.getObject("created_by", UUID.class),
                rs.getString("created_by_name"),
                rs.getTimestamp("created_at").toInstant(),
                instant(rs.getTimestamp("finished_at")),
                rs.getObject("sent_conversation_id", UUID.class),
                rs.getObject("message_id", UUID.class));
    }

    private static FollowUpTemplate template(ResultSet rs, int row) throws SQLException {
        return new FollowUpTemplate(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("body"),
                whatsapp(rs), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static WhatsAppTemplate whatsapp(ResultSet rs) throws SQLException {
        String name = rs.getString("whatsapp_template");
        return name == null ? null : new WhatsAppTemplate(name, rs.getString("whatsapp_language"),
                rs.getBoolean("whatsapp_name_parameter"));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
