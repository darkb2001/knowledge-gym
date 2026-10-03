package com.knowledgegym.infrastructure.security;

import com.knowledgegym.infrastructure.TestPostgres;
import com.knowledgegym.infrastructure.persistence.adapter.JdbcAdminLearningAdapter;
import com.knowledgegym.learning.application.AdminLearningUseCase;
import com.knowledgegym.learning.domain.port.AdminLearningPort;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AdminLearningPersistenceTest {
    private static final TestPostgres DB = new TestPostgres();
    private static JdbcTemplate jdbc; private static TransactionTemplate tx;
    private JdbcAdminLearningAdapter adapter; private UUID user, card;
    @BeforeAll static void migrate() {
        DB.start(); var ds=DB.dataSource();
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds); tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
    }
    @AfterAll static void stop() { DB.close(); }
    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE users,topics CASCADE");
        user=UUID.randomUUID(); card=UUID.randomUUID();
        UUID topic=UUID.randomUUID(),module=UUID.randomUUID(),question=UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,display_name,xp) VALUES (?,'u@example.com','Learner',10)",user);
        jdbc.update("INSERT INTO topics(id,name,slug) VALUES (?,'Topic','topic')",topic);
        jdbc.update("INSERT INTO modules(id,topic_id,name,slug) VALUES (?,?,'Module','module')",module,topic);
        jdbc.update("INSERT INTO questions(id,module_id,title,answer_html,difficulty,sort_order) VALUES (?,?,'Question','Answer','MID',1)",question,module);
        jdbc.update("INSERT INTO srs_cards(id,user_id,question_id,repetitions,interval_days,next_review) VALUES (?,?,?,5,20,CURRENT_DATE+20)",card,user,question);
        jdbc.update("INSERT INTO study_attempts(user_id,question_id,source,is_correct) VALUES (?,?,'FLASHCARD',true)",user,question);
        jdbc.update("INSERT INTO user_progress(user_id,module_id,mastery_pct,total_attempts,correct_count) VALUES (?,?,100,1,1)",user,module);
        jdbc.update("INSERT INTO quiz_sessions(user_id,strategy,total) VALUES (?,'RANDOM',1)",user);
        jdbc.update("INSERT INTO interview_sessions(user_id,topic_id,question_count,mode) VALUES (?,?,1,'TEXT')",user,topic);
        adapter=new JdbcAdminLearningAdapter(jdbc);
    }
    @Test void everyLearningDirectoryIsPaginated() {
        for (var kind:AdminLearningPort.Kind.values()) {
            var result=adapter.list(user,kind,1,20);
            assertEquals(1,result.totalElements()); assertEquals(1,result.items().size());
            assertTrue(adapter.list(user,kind,2,20).items().isEmpty());
        }
    }
    @Test void resetOnlyChangesSchedulingAndAuditsWithoutDeletingHistory() {
        var service=new AdminLearningUseCase(adapter);
        tx.executeWithoutResult(s->service.resetCard(user,user,card,"Support request"));
        assertEquals(0,jdbc.queryForObject("SELECT repetitions FROM srs_cards WHERE id=?",Integer.class,card));
        assertEquals(0,jdbc.queryForObject("SELECT interval_days FROM srs_cards WHERE id=?",Integer.class,card));
        assertEquals(10,jdbc.queryForObject("SELECT xp FROM users WHERE id=?",Integer.class,user));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM study_attempts",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT total_attempts FROM user_progress WHERE user_id=?",Integer.class,user));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=?",Integer.class,card));
    }
    @Test void wrongOwnerCannotResetAndRollbackPreservesSchedule() {
        assertThrows(com.knowledgegym.shared.application.NotFoundException.class,
                ()->tx.executeWithoutResult(s->adapter.resetCard(user,UUID.randomUUID(),card,"Request")));
        tx.executeWithoutResult(s->{adapter.resetCard(user,user,card,"Request");s.setRollbackOnly();});
        assertEquals(5,jdbc.queryForObject("SELECT repetitions FROM srs_cards WHERE id=?",Integer.class,card));
    }
}
