package com.knowledgegym.infrastructure.blog;

import com.knowledgegym.blog.domain.port.LearningContentDraftPort.*;
import com.knowledgegym.infrastructure.TestPostgres;
import com.knowledgegym.infrastructure.search.JdbcSearchDocumentAdapter;
import com.knowledgegym.search.domain.model.SearchDocument;
import com.knowledgegym.shared.application.ConflictException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class LearningDraftPersistenceTest {
    static final TestPostgres DB = new TestPostgres();
    static JdbcTemplate db;
    static TransactionTemplate tx;
    JdbcLearningContentDraftAdapter drafts;
    JdbcLearningDraftMaterializerAdapter materializer;
    UUID actor, module;
    static final String PAYLOAD = """
        {"title":"Java memory","answerHtml":"<p>Memory explanation</p><script>bad()</script>","difficulty":"MID","tags":["java"],
         "options":[{"content":"Heap","isCorrect":true},{"content":"Other","isCorrect":false}]}
        """;
    @BeforeAll static void migrate() {
        DB.start(); var ds=DB.dataSource();
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        db=new JdbcTemplate(ds); tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
    }
    @AfterAll static void stop(){DB.close();}
    @BeforeEach void setup(){
        db.execute("TRUNCATE users,topics CASCADE"); actor=UUID.randomUUID();module=UUID.randomUUID();UUID topic=UUID.randomUUID();
        db.update("INSERT INTO users(id,email,display_name,role) VALUES(?,'admin@example.com','Admin','ADMIN')",actor);
        db.update("INSERT INTO topics(id,name,slug) VALUES(?,'Java','java')",topic);
        db.update("INSERT INTO modules(id,topic_id,name,slug) VALUES(?,?,'Core','core')",module,topic);
        drafts=new JdbcLearningContentDraftAdapter(db,new ObjectMapper());
        materializer=new JdbcLearningDraftMaterializerAdapter(db,new ObjectMapper(),null,null,null,
                html->html.replaceAll("(?is)<script.*?</script>",""));
    }
    Draft draft(Kind kind,String payload){return tx.execute(s->drafts.save(actor,null,new Generated(kind,"Title",payload,List.of(),"test",10,BigDecimal.ZERO)));}
    Draft approved(Kind kind,String payload){var d=draft(kind,payload);return tx.execute(s->drafts.review(actor,d.id(),Status.APPROVED,"Checked"));}
    UUID materialize(Draft d){return tx.execute(s->materializer.materialize(d.id(),module,actor,"Reviewed"));}
    int count(String table){return db.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    @Test void providerGenerationIsDisabledByDefaultEvenWithConfiguredKey(){
        var provider=new OpenAiLearningContentAdapter(org.springframework.web.client.RestClient.builder(),new ObjectMapper(),"test-key","https://example.invalid","test-model");
        var error=assertThrows(IllegalStateException.class,()->provider.generate(Kind.QUESTION,"Topic","Instruction",List.of()));
        assertTrue(error.getMessage().contains("disabled"));
    }

    @Test void saveAndReviewRoundTripAndRejectRepeatedReview(){
        var d=draft(Kind.QUESTION,PAYLOAD);assertEquals(Status.REVIEW,drafts.get(d.id()).status());
        assertEquals(1,drafts.list(Status.REVIEW,null,1,20).totalElements());
        tx.executeWithoutResult(s->drafts.review(actor,d.id(),Status.APPROVED,"Verified"));
        assertThrows(ConflictException.class,()->tx.executeWithoutResult(s->drafts.review(actor,d.id(),Status.REJECTED,"Changed mind")));
        assertEquals(2,count("learning_draft_audit"));
    }
    @Test void materializesSanitizedDraftWithOptionsAndRegistry(){
        var id=materialize(approved(Kind.QUESTION,PAYLOAD));
        assertEquals("DRAFT",db.queryForObject("SELECT content_status FROM questions WHERE id=?",String.class,id));
        assertFalse(db.queryForObject("SELECT answer_html FROM questions WHERE id=?",String.class,id).contains("script"));
        assertEquals(2,count("question_options"));assertEquals(1,count("learning_draft_materializations"));
        assertFalse(db.queryForObject("SELECT searchable_text FROM questions WHERE id=?",String.class,id).isBlank());
    }
    @Test void duplicateContentRollsBackWithoutOrphanQuestion(){
        materialize(approved(Kind.QUESTION,PAYLOAD));
        var second=approved(Kind.QUESTION,PAYLOAD);
        assertThrows(ConflictException.class,()->materialize(second));
        assertEquals(1,count("questions"));assertEquals(1,count("learning_draft_materializations"));
    }
    @Test void invalidOptionsAndUnapprovedDraftNeverCreateQuestion(){
        assertThrows(ConflictException.class,()->materialize(draft(Kind.QUESTION,PAYLOAD)));
        assertThrows(IllegalArgumentException.class,()->materialize(approved(Kind.QUESTION,PAYLOAD.replace("\"isCorrect\":false","\"isCorrect\":true"))));
        assertEquals(0,count("questions"));
    }
    @Test void transactionFailureRollsBackAllWrites(){
        var d=approved(Kind.QUESTION,PAYLOAD);
        tx.executeWithoutResult(s->{materializer.materialize(d.id(),module,actor,"Check");s.setRollbackOnly();});
        assertEquals(0,count("questions"));assertEquals(0,count("question_options"));assertEquals(0,count("learning_draft_materializations"));
    }
    @Test void concurrentRequestsCreateExactlyOneQuestion() throws Exception {
        var d=approved(Kind.QUESTION,PAYLOAD);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> request=()->{try{materialize(d);return true;}catch(ConflictException e){return false;}};
            var results=pool.invokeAll(List.of(request,request));
            assertEquals(1,results.stream().filter(f->{try{return f.get();}catch(Exception e){throw new RuntimeException(e);}}).count());
            assertEquals(1,count("questions"));
        } finally {pool.shutdownNow();}
    }
    @Test void flashcardAndInterviewDoNotEnrollUsers(){
        materialize(approved(Kind.FLASHCARD,"{\"front\":\"Card\",\"backHtml\":\"<p>Answer</p>\"}"));
        materialize(approved(Kind.INTERVIEW,"{\"question\":\"Interview\",\"idealAnswerHtml\":\"<p>Answer</p>\"}"));
        assertEquals(2,count("questions"));assertEquals(0,count("srs_cards"));assertEquals(0,count("interview_sessions"));
    }
    @Test void rollbackHidesContentButPreservesStudyHistoryAndRegistry(){
        var d=approved(Kind.QUESTION,PAYLOAD);UUID id=materialize(d);
        db.update("UPDATE questions SET content_status='PUBLISHED' WHERE id=?",id);
        db.update("INSERT INTO srs_cards(user_id,question_id) VALUES(?,?)",actor,id);
        db.update("INSERT INTO study_attempts(user_id,question_id,source,is_correct) VALUES(?,?,'FLASHCARD',true)",actor,id);
        tx.executeWithoutResult(s->materializer.rollback(d.id(),actor,"Incorrect fact"));
        assertEquals("HIDDEN",db.queryForObject("SELECT content_status FROM questions WHERE id=?",String.class,id));
        assertEquals(1,count("srs_cards"));assertEquals(1,count("study_attempts"));assertEquals(1,count("learning_draft_materializations"));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM learning_draft_audit WHERE action='ROLLBACK_HIDE'",Integer.class));
        assertThrows(ConflictException.class,()->materialize(d));
    }

    @Test void goalSourceSelectionExcludesHttpAndOtherDomains(){
        tx.executeWithoutResult(s -> {
            UUID https=UUID.randomUUID();
            db.update("INSERT INTO collector_sources(id,name,type,url) VALUES(?,'HTTPS','RSS','https://fixture.example.com/feed')",https);
            db.update("INSERT INTO collector_sources(name,type,url) VALUES('HTTP','RSS','http://fixture.example.com/feed')");
            db.update("INSERT INTO collector_sources(name,type,url) VALUES('Other','RSS','https://other.example.com/feed')");
            var collector=new JdbcCollectorRepository(db,new ObjectMapper());
            assertEquals(List.of(https),collector.findDueSourcesForDomains(List.of("fixture.example.com")).stream().map(source -> source.id()).toList());
            assertTrue(collector.findDueSourcesForDomains(List.of()).isEmpty());
            s.setRollbackOnly();
        });
    }

    @Test void searchProjectionExcludesDraftAndHideEmitsOutbox(){
        UUID id=materialize(approved(Kind.QUESTION,PAYLOAD));var search=new JdbcSearchDocumentAdapter(db);
        assertTrue(search.load(SearchDocument.DocType.QUESTION,id).isEmpty());
        db.update("UPDATE questions SET content_status='PUBLISHED' WHERE id=?",id);
        assertTrue(search.load(SearchDocument.DocType.QUESTION,id).isPresent());
        long before=db.queryForObject("SELECT count(*) FROM search_outbox WHERE doc_id=?",Long.class,id);
        db.update("UPDATE questions SET content_status='HIDDEN' WHERE id=?",id);
        assertTrue(search.load(SearchDocument.DocType.QUESTION,id).isEmpty());
        assertEquals(before+1,db.queryForObject("SELECT count(*) FROM search_outbox WHERE doc_id=?",Long.class,id));
    }
}
