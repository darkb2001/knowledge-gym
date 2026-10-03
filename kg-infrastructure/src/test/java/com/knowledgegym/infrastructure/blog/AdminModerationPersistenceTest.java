package com.knowledgegym.infrastructure.blog;

import com.knowledgegym.blog.application.ModerateBlogUseCase;
import com.knowledgegym.blog.domain.port.AdminModerationPort;
import com.knowledgegym.infrastructure.TestPostgres;
import com.knowledgegym.shared.application.ConflictException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AdminModerationPersistenceTest {
    private static final TestPostgres DB = new TestPostgres();
    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private JdbcAdminModerationAdapter adapter;
    private JdbcBlogPostRepository posts;
    private ModerateBlogUseCase service;
    private UUID actor, post, comment;
    @BeforeAll static void migrate() {
        DB.start(); var ds = DB.dataSource();
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(ds); tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    }
    @AfterAll static void stop() { DB.close(); }
    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE users CASCADE");
        actor = UUID.randomUUID(); post = UUID.randomUUID(); comment = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,display_name,role) VALUES (?,'owner@example.com','Owner','ADMIN')",actor);
        jdbc.update("INSERT INTO blog_posts(id,author_id,author_type,title,slug,body,status) VALUES (?,?,'HUMAN','Post','test-post','<p>Body</p>','PUBLISHED')",post,actor);
        jdbc.update("INSERT INTO blog_comments(id,post_id,user_id,content) VALUES (?,?,?,'Useful comment')",comment,post,actor);
        adapter = new JdbcAdminModerationAdapter(new NamedParameterJdbcTemplate(jdbc));
        posts = new JdbcBlogPostRepository(jdbc,new ObjectMapper(),"https://example.com");
        service = new ModerateBlogUseCase(adapter);
    }
    @Test void deletedCommentsDisappearAndRestoreRequiresExplicitVisibility() {
        assertEquals(1,posts.comments(post).size());
        tx.executeWithoutResult(s->service.comment(actor,comment,AdminModerationPort.CommentStatus.DELETED,false,"Spam"));
        assertTrue(posts.comments(post).isEmpty());
        assertEquals(1,adapter.comments(null,post,null,AdminModerationPort.CommentStatus.DELETED,1,20).totalElements());
        assertThrows(ConflictException.class,()->tx.executeWithoutResult(s->service.comment(actor,comment,AdminModerationPort.CommentStatus.VISIBLE,false,"Show")));
        tx.executeWithoutResult(s->service.comment(actor,comment,AdminModerationPort.CommentStatus.HIDDEN,true,"Appeal"));
        assertTrue(posts.comments(post).isEmpty());
        tx.executeWithoutResult(s->service.comment(actor,comment,AdminModerationPort.CommentStatus.VISIBLE,false,"Approved"));
        assertEquals(1,posts.comments(post).size());
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=?",Integer.class,comment));
    }
    @Test void postRemovalHidesPublicContentAndRestoresOnlyToReview() {
        tx.executeWithoutResult(s->service.post(actor,post,AdminModerationPort.PostAction.DELETE,"Outdated"));
        assertTrue(posts.findPublishedBySlug("test-post").isEmpty());
        assertTrue(posts.findPublished(0,20,null).isEmpty());
        assertEquals(1,posts.searchAdmin("DELETED",null,null,1,20).totalElements());
        assertThrows(ConflictException.class,()->tx.executeWithoutResult(s->service.post(actor,post,AdminModerationPort.PostAction.HIDE,"Hide")));
        tx.executeWithoutResult(s->service.post(actor,post,AdminModerationPort.PostAction.RESTORE,"Corrected"));
        assertEquals("REVIEW",posts.findById(post).orElseThrow().status());
        assertTrue(posts.findPublishedBySlug("test-post").isEmpty());
        tx.executeWithoutResult(s->posts.publish(post));
        assertTrue(posts.findPublishedBySlug("test-post").isPresent());
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM search_outbox WHERE doc_id=?",Long.class,post)>0);
    }
    @Test void auditAndStatusRollbackTogether() {
        tx.executeWithoutResult(s->{ service.post(actor,post,AdminModerationPort.PostAction.HIDE,"Check"); s.setRollbackOnly(); });
        assertEquals("PUBLISHED",posts.findById(post).orElseThrow().status());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=?",Integer.class,post));
    }
    @Test void manualDraftCanBeEditedButRemovedOrPublicPostCannotBeResurrectedByEdit() {
        var writer=new JdbcBlogWriterRepository(jdbc,new ObjectMapper());
        var editor=new com.knowledgegym.blog.application.BlogWriterAdminUseCase(null,writer,
                new com.knowledgegym.blog.domain.service.QualityScorer(),20,80000,10);
        jdbc.update("UPDATE blog_posts SET status='DRAFT' WHERE id=?",post);
        tx.executeWithoutResult(s->editor.manualEdit(post,actor,"Edited draft","<p>Updated answer</p><script>bad()</script>",null,
                html->html.replaceAll("(?is)<script.*?</script>","")));
        assertEquals("<p>Updated answer</p>",posts.findById(post).orElseThrow().body());
        assertEquals("DRAFT",posts.findById(post).orElseThrow().status());
        assertEquals(1,writer.revisions(post).size());
        tx.executeWithoutResult(s->service.post(actor,post,AdminModerationPort.PostAction.DELETE,"Remove"));
        assertThrows(IllegalArgumentException.class,()->editor.manualEdit(post,actor,"Title","<p>Body</p>",null,html->html));
        assertEquals("DELETED",posts.findById(post).orElseThrow().status());
    }
    @Test void cannotReplyToHiddenCommentOrCommentOnRemovedPost() {
        tx.executeWithoutResult(s->service.comment(actor,comment,AdminModerationPort.CommentStatus.HIDDEN,false,"Review"));
        assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(s->posts.addComment(post,actor,comment,"Reply")));
        tx.executeWithoutResult(s->service.post(actor,post,AdminModerationPort.PostAction.HIDE,"Review"));
        assertThrows(com.knowledgegym.shared.application.NotFoundException.class,()->tx.executeWithoutResult(s->posts.addComment(post,actor,null,"Comment")));
    }
}
