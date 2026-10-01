-- Materialize attempts recorded by m5/m6 before the m7 write path existed.
WITH first_attempts AS (
    SELECT DISTINCT ON (user_id, question_id)
           user_id,
           CASE WHEN is_correct
                THEN CASE source WHEN 'FLASHCARD' THEN 10
                                 WHEN 'DAILY' THEN 15
                                 ELSE 5 END
                ELSE 1 END AS xp
    FROM study_attempts
    ORDER BY user_id, question_id, attempted_at, id
), xp_totals AS (
    SELECT user_id, SUM(xp)::integer AS xp
    FROM first_attempts
    GROUP BY user_id
)
UPDATE users u
SET xp = COALESCE((SELECT t.xp FROM xp_totals t WHERE t.user_id = u.id), 0);

-- Replace any skeleton rows with totals derived from the canonical attempt table.
INSERT INTO user_progress
    (id, user_id, module_id, total_attempts, correct_count, mastery_pct,
     streak_days, last_active_at, updated_at)
SELECT gen_random_uuid(), a.user_id, q.module_id,
       COUNT(*)::integer,
       COUNT(*) FILTER (WHERE a.is_correct)::integer,
       ROUND(COUNT(*) FILTER (WHERE a.is_correct)::numeric * 100 / COUNT(*), 2),
       0, MAX(a.attempted_at), NOW()
FROM study_attempts a
JOIN questions q ON q.id = a.question_id
GROUP BY a.user_id, q.module_id
ON CONFLICT (user_id, module_id) DO UPDATE SET
    total_attempts = EXCLUDED.total_attempts,
    correct_count = EXCLUDED.correct_count,
    mastery_pct = EXCLUDED.mastery_pct,
    streak_days = 0,
    last_active_at = EXCLUDED.last_active_at,
    updated_at = NOW();
