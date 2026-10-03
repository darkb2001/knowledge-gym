package com.knowledgegym.infrastructure.persistence;

import com.knowledgegym.infrastructure.TestPostgres;
import com.knowledgegym.infrastructure.persistence.dao.QuestionDependentsDao;
import com.knowledgegym.shared.application.ConflictException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Executes guard SQL against disposable PostgreSQL through a mocked EntityManager/JDBC bridge. */
class QuestionDeleteSafetyTest {
    static final TestPostgres DB = new TestPostgres();
    static JdbcTemplate db;
    static TransactionTemplate tx;
    QuestionDependentsDao guard;
    UUID actor, module, question;
    @BeforeAll static void migrate() {
        DB.start(); var ds = DB.dataSource();
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        db = new JdbcTemplate(ds); tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    }
    @AfterAll static void stop() { DB.close(); }
    @BeforeEach void setup() {
        db.execute("TRUNCATE users,topics CASCADE");
        actor = UUID.randomUUID(); module = UUID.randomUUID(); question = UUID.randomUUID(); var topic = UUID.randomUUID();
        db.update("INSERT INTO users(id,email,display_name) VALUES(?,'guard@example.com','Guard')", actor);
        db.update("INSERT INTO topics(id,name,slug) VALUES(?,'Java','java')", topic);
        db.update("INSERT INTO modules(id,topic_id,name,slug) VALUES(?,?,'Core','core')", module, topic);
        db.update("INSERT INTO questions(id,module_id,title,answer_html,difficulty,sort_order) VALUES(?,?,'Question','<p>Answer</p>','MID',1)", question,module);
        var jdbc = new NamedParameterJdbcTemplate(db);
        var em = mock(EntityManager.class);
        when(em.createNativeQuery(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0); var params = new HashMap<String,Object>(); var query = mock(Query.class);
            when(query.setParameter(anyString(), any())).thenAnswer(binding -> { params.put(binding.getArgument(0), binding.getArgument(1)); return query; });
            when(query.getResultList()).thenAnswer(result -> jdbc.queryForList(sql, params));
            when(query.getSingleResult()).thenAnswer(result -> jdbc.queryForObject(sql, params, Long.class));
            return query;
        });
        guard = new QuestionDependentsDao(); ReflectionTestUtils.setField(guard,"entityManager",em);
    }
    @Test void neverDeletesSrsOrAttemptsToPermitHardDelete() {
        db.update("INSERT INTO srs_cards(user_id,question_id) VALUES(?,?)", actor,question);
        db.update("INSERT INTO study_attempts(user_id,question_id,source,is_correct) VALUES(?,?,'FLASHCARD',true)", actor,question);
        assertThrows(ConflictException.class, () -> tx.executeWithoutResult(s -> guard.deleteForQuestion(question)));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM srs_cards",Integer.class));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM study_attempts",Integer.class));
    }
    @Test void reimportCannotRemoveQuestionWithNotesButMayKeepItsOrder() {
        db.update("INSERT INTO notes(user_id,question_id,note_type,content) VALUES(?,?,'QUICK','Keep')",actor,question);
        assertThrows(ConflictException.class, () -> tx.executeWithoutResult(s -> guard.deleteForModule(module,List.of(2))));
        tx.executeWithoutResult(s -> guard.deleteForModule(module,List.of(1)));
        assertEquals(question,db.queryForObject("SELECT question_id FROM notes",UUID.class));
    }
    @Test void unreferencedQuestionsCanStillBeRemoved() {
        tx.executeWithoutResult(s -> guard.deleteForQuestion(question));
        tx.executeWithoutResult(s -> guard.deleteForModule(module,List.of()));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM questions",Integer.class));
    }
}
