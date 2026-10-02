package com.knowledgegym.infrastructure.blog;

import tools.jackson.databind.ObjectMapper;
import com.knowledgegym.blog.domain.model.CollectedItem;
import com.knowledgegym.blog.domain.model.CollectorSource;
import com.knowledgegym.blog.domain.port.CollectorRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class JdbcCollectorRepository implements CollectorRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcCollectorRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<CollectorSource> findDueSources() {
        return jdbc.query("SELECT id,name,type,url,config::text,fetch_interval_sec,last_fetched_at FROM collector_sources " +
                        "WHERE active AND (last_fetched_at IS NULL OR last_fetched_at + make_interval(secs => fetch_interval_sec) <= NOW()) ORDER BY name",
                (rs, row) -> new CollectorSource(rs.getObject("id", UUID.class), rs.getString("name"),
                        CollectorSource.Type.valueOf(rs.getString("type")), rs.getString("url"), rs.getString("config"),
                        rs.getInt("fetch_interval_sec"), rs.getTimestamp("last_fetched_at") == null ? null : rs.getTimestamp("last_fetched_at").toInstant()));
    }

    @Override
    @Transactional
    public int saveCollected(UUID sourceId, List<CollectedItem> items) {
        int inserted = 0;
        for (CollectedItem item : items) {
            UUID id = UUID.randomUUID();
            List<UUID> result = jdbc.query(connection -> {
                var ps = connection.prepareStatement("INSERT INTO collected_items(id,source_id,title,url,summary,content_hash,score,category,tags,published_at) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING RETURNING id");
                ps.setObject(1, id);
                ps.setObject(2, sourceId);
                ps.setString(3, item.title());
                ps.setString(4, item.url());
                ps.setString(5, item.summary());
                ps.setString(6, item.contentHash());
                ps.setDouble(7, item.score());
                ps.setString(8, item.category());
                ps.setArray(9, connection.createArrayOf("text", item.tags().toArray()));
                ps.setTimestamp(10, item.publishedAt() == null ? null : Timestamp.from(item.publishedAt()));
                return ps;
            }, (rs, row) -> rs.getObject(1, UUID.class));
            if (!result.isEmpty()) {
                inserted++;
                String payload = writeJson(Map.of("itemId", id, "sourceId", sourceId));
                jdbc.update("INSERT INTO event_outbox(id,aggregate_type,aggregate_id,event_type,payload) VALUES(?,?,?,?,?::jsonb)",
                        UUID.randomUUID(), "collected_item", id, "collector.item_collected.v1", payload);
            }
        }
        // Always advance the source clock in the same TX as inserts (even when all items were dupes).
        markFetched(sourceId);
        return inserted;
    }

    @Override
    public void markFetched(UUID sourceId) {
        jdbc.update("UPDATE collector_sources SET last_fetched_at=NOW() WHERE id=?", sourceId);
    }

    @Override
    public void scoreItem(UUID itemId) {
        jdbc.update("UPDATE collected_items SET category=CASE " +
                "WHEN lower(title||' '||coalesce(summary,'')) LIKE '%spring%' THEN 'spring' " +
                "WHEN lower(title||' '||coalesce(summary,'')) LIKE '%database%' OR lower(title||' '||coalesce(summary,'')) LIKE '%hibernate%' OR lower(title||' '||coalesce(summary,'')) LIKE '%sql%' THEN 'database' " +
                "WHEN lower(title||' '||coalesce(summary,'')) LIKE '%kafka%' OR lower(title||' '||coalesce(summary,'')) LIKE '%microservice%' THEN 'architecture' ELSE 'java' END, " +
                "score=LEAST(100,40 + (CASE WHEN lower(title||' '||coalesce(summary,'')) LIKE '%java%' THEN 7 ELSE 0 END) + " +
                "(CASE WHEN lower(title||' '||coalesce(summary,'')) LIKE '%spring%' THEN 7 ELSE 0 END) + " +
                "(CASE WHEN lower(title||' '||coalesce(summary,'')) LIKE '%security%' THEN 7 ELSE 0 END) + " +
                "(CASE WHEN published_at>NOW()-INTERVAL '30 days' THEN 10 ELSE 0 END)) WHERE id=?", itemId);
    }

    @Override
    public UUID startRun(String inputSummary) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_runs(id,agent_type,status,input_summary) VALUES(?, 'COLLECTOR','RUNNING',?)", id, inputSummary);
        return id;
    }

    @Override
    public void finishRun(UUID runId, boolean succeeded, String outputSummary, String errorMessage) {
        jdbc.update("UPDATE agent_runs SET status=?,finished_at=NOW(),output_summary=?,error_message=? WHERE id=?",
                succeeded ? "SUCCESS" : "FAILED", outputSummary, errorMessage, runId);
    }

    private String writeJson(Object payload) {
        try { return json.writeValueAsString(payload); }
        catch (Exception e) { throw new IllegalStateException("Could not serialize outbox event", e); }
    }
}
