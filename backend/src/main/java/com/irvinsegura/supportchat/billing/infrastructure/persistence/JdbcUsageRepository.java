package com.irvinsegura.supportchat.billing.infrastructure.persistence;

import com.irvinsegura.supportchat.billing.UsageMetric;
import com.irvinsegura.supportchat.billing.domain.port.UsageRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcUsageRepository implements UsageRepository {

    private final JdbcClient jdbc;

    JdbcUsageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // One statement, so concurrent answers of the same workspace add up instead of overwriting each other
    @Override
    @Transactional
    public void add(UUID workspaceId, LocalDate day, UsageMetric metric, String dimension, long quantity) {
        jdbc.sql("""
                        INSERT INTO usage_daily (workspace_id, day, metric, dimension, quantity)
                        VALUES (:workspaceId, :day, :metric, :dimension, :quantity)
                        ON CONFLICT (workspace_id, day, metric, dimension)
                        DO UPDATE SET quantity = usage_daily.quantity + EXCLUDED.quantity
                        """)
                .param("workspaceId", workspaceId)
                .param("day", day)
                .param("metric", metric.name())
                .param("dimension", dimension)
                .param("quantity", quantity)
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyUsage> between(LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT day, metric, dimension, quantity
                        FROM usage_daily
                        WHERE day BETWEEN :from AND :to
                        ORDER BY day, metric, dimension
                        """)
                .param("from", from)
                .param("to", to)
                .query((rs, row) -> new DailyUsage(rs.getObject("day", LocalDate.class),
                        UsageMetric.valueOf(rs.getString("metric")), rs.getString("dimension"),
                        rs.getLong("quantity")))
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public long total(UsageMetric metric, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT COALESCE(SUM(quantity), 0)
                        FROM usage_daily
                        WHERE metric = :metric AND day BETWEEN :from AND :to
                        """)
                .param("metric", metric.name())
                .param("from", from)
                .param("to", to)
                .query(Long.class)
                .single();
    }

    @Override
    @Transactional
    public boolean noteAlertIfNew(UUID workspaceId, YearMonth month, UsageMetric metric, int percent, Instant at) {
        return jdbc.sql("""
                        INSERT INTO usage_alerts (workspace_id, month, metric, percent, reached_at)
                        VALUES (:workspaceId, :month, :metric, :percent, :at)
                        ON CONFLICT DO NOTHING
                        """)
                .param("workspaceId", workspaceId)
                .param("month", month.atDay(1))
                .param("metric", metric.name())
                .param("percent", percent)
                .param("at", Timestamp.from(at))
                .update() == 1;
    }

    @Override
    @Transactional
    public boolean addTopUp(UUID workspaceId, YearMonth month, UsageMetric metric, long quantity, String topUpCode,
            String reference, Instant paidAt) {
        return jdbc.sql("""
                        INSERT INTO usage_top_ups (reference, workspace_id, month, metric, quantity, top_up, paid_at)
                        VALUES (:reference, :workspaceId, :month, :metric, :quantity, :topUp, :paidAt)
                        ON CONFLICT (reference) DO NOTHING
                        """)
                .param("reference", reference)
                .param("workspaceId", workspaceId)
                .param("month", month.atDay(1))
                .param("metric", metric.name())
                .param("quantity", quantity)
                .param("topUp", topUpCode)
                .param("paidAt", Timestamp.from(paidAt))
                .update() == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UsageMetric, Long> topUps(YearMonth month) {
        Map<UsageMetric, Long> extra = new EnumMap<>(UsageMetric.class);
        jdbc.sql("""
                        SELECT metric, SUM(quantity) AS quantity
                        FROM usage_top_ups
                        WHERE month = :month
                        GROUP BY metric
                        """)
                .param("month", month.atDay(1))
                .query(rs -> {
                    extra.put(UsageMetric.valueOf(rs.getString("metric")), rs.getLong("quantity"));
                });
        return extra;
    }

    @Override
    @Transactional
    public void forgetAlerts(YearMonth month, UsageMetric metric) {
        jdbc.sql("DELETE FROM usage_alerts WHERE month = :month AND metric = :metric")
                .param("month", month.atDay(1))
                .param("metric", metric.name())
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public long reported(YearMonth month, UsageMetric metric) {
        return jdbc.sql("SELECT quantity FROM usage_reports WHERE month = :month AND metric = :metric")
                .param("month", month.atDay(1))
                .param("metric", metric.name())
                .query(Long.class)
                .optional()
                .orElse(0L);
    }

    @Override
    @Transactional
    public void markReported(UUID workspaceId, YearMonth month, UsageMetric metric, long quantity, Instant at) {
        jdbc.sql("""
                        INSERT INTO usage_reports (workspace_id, month, metric, quantity, reported_at)
                        VALUES (:workspaceId, :month, :metric, :quantity, :at)
                        ON CONFLICT (workspace_id, month, metric)
                        DO UPDATE SET quantity = EXCLUDED.quantity, reported_at = EXCLUDED.reported_at
                        """)
                .param("workspaceId", workspaceId)
                .param("month", month.atDay(1))
                .param("metric", metric.name())
                .param("quantity", quantity)
                .param("at", Timestamp.from(at))
                .update();
    }
}
