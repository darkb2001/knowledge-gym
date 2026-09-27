-- V010 — Materialized view: user_topic_mastery
-- Aggregate user_progress theo topic (join qua modules → topics).

CREATE MATERIALIZED VIEW user_topic_mastery AS
SELECT
    up.user_id,
    t.id AS topic_id,
    AVG(up.mastery_pct) AS mastery_pct,
    SUM(up.total_attempts) AS total_attempts
FROM user_progress up
JOIN modules m ON m.id = up.module_id
JOIN topics t ON t.id = m.topic_id
GROUP BY up.user_id, t.id;

CREATE UNIQUE INDEX idx_utm_user_topic ON user_topic_mastery (user_id, topic_id);