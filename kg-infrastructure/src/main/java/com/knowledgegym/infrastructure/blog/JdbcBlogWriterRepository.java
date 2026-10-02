package com.knowledgegym.infrastructure.blog;

import tools.jackson.databind.ObjectMapper;
import com.knowledgegym.blog.domain.model.BlogPost;
import com.knowledgegym.blog.domain.port.AiWriterPort;
import com.knowledgegym.blog.domain.port.BlogWriterRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.util.*;

@Repository
public class JdbcBlogWriterRepository implements BlogWriterRepository {
    private static final String COLUMNS="id,author_id,author_type,title,slug,body,excerpt,source_module_id,source_question_id,status,published_at,view_count,like_count,tags,created_at";
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public JdbcBlogWriterRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    @Override public List<AiWriterPort.Source> selectSources(int limit){return jdbc.query("SELECT id,title,summary,url FROM collected_items WHERE used_in_post_id IS NULL AND url LIKE 'https://%' ORDER BY score DESC NULLS LAST,published_at DESC NULLS LAST,collected_at DESC LIMIT ?",(rs,n)->new AiWriterPort.Source(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4)),Math.max(1,Math.min(20,limit)));}
    @Override public List<AiWriterPort.Source> findSources(List<UUID> ids){if(ids==null||ids.isEmpty())return List.of();return jdbc.query(connection->{var ps=connection.prepareStatement("SELECT id,title,summary,url FROM collected_items WHERE id=ANY(?) AND url LIKE 'https://%'");ps.setArray(1,connection.createArrayOf("uuid",ids.toArray()));return ps;},(rs,n)->new AiWriterPort.Source(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4)));}

    @Override @Transactional
    public BlogPost createReviewDraft(AiWriterPort.Completion c,List<UUID> sourceIds,int score,UUID queueId){
        UUID id=UUID.randomUUID();String slug=slug(c.title());List<String> tags=new ArrayList<>(c.seoKeywords().stream().filter(Objects::nonNull).map(s->s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}-]","-")).filter(s->!s.isBlank()).map(s->s.substring(0,Math.min(50,s.length()))).limit(10).toList());tags.add("ai-generated");
        jdbc.update(connection->{var ps=connection.prepareStatement("INSERT INTO blog_posts(id,author_id,author_type,title,slug,body,excerpt,status,tags,quality_score,seo_title,seo_description,seo_keywords,ai_model) VALUES(?,NULL,'AI',?,?,?,?,'REVIEW',?,?,?,?,?,?)");ps.setObject(1,id);ps.setString(2,c.title());ps.setString(3,slug);ps.setString(4,c.bodyHtml());ps.setString(5,c.excerpt());ps.setArray(6,connection.createArrayOf("text",tags.toArray()));ps.setInt(7,score);ps.setString(8,c.seoTitle());ps.setString(9,c.seoDescription());ps.setArray(10,connection.createArrayOf("text",c.seoKeywords().toArray()));ps.setString(11,c.model());return ps;});
        storeRevision(id,1,c,sourceIds,"Initial AI generation",score,null);
        if(!sourceIds.isEmpty())jdbc.update("UPDATE collected_items SET used_in_post_id=? WHERE id=ANY(?) AND used_in_post_id IS NULL",id,sourceIds.toArray(UUID[]::new));
        if(queueId!=null)jdbc.update("UPDATE blog_generation_queue SET generated_post_id=? WHERE id=?",id,queueId);
        return findPost(id).orElseThrow();
    }
    @Override @Transactional
    public int appendRevision(UUID postId, AiWriterPort.Completion c, List<UUID> sourceIds, String instruction, int score, UUID actor) {
        // Lock the draft row so concurrent revise/edit cannot race on version numbers.
        Integer locked = jdbc.query(
                "SELECT 1 FROM blog_posts WHERE id=? AND status='REVIEW' FOR UPDATE",
                (rs, n) -> rs.getInt(1), postId).stream().findFirst().orElse(null);
        if (locked == null) throw new IllegalArgumentException("Only review drafts can be revised");
        int version = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version),0)+1 FROM blog_draft_revisions WHERE post_id=?", Integer.class, postId);
        int updated = jdbc.update(
                "UPDATE blog_posts SET title=?,body=?,excerpt=?,quality_score=?,seo_title=COALESCE(?::text,seo_title),seo_description=COALESCE(?::text,seo_description),seo_keywords=COALESCE(?::text[],seo_keywords),ai_model=? WHERE id=? AND status='REVIEW'",
                c.title(), c.bodyHtml(), c.excerpt(), score, c.seoTitle(), c.seoDescription(),
                c.seoKeywords().isEmpty() ? null : c.seoKeywords().toArray(String[]::new), c.model(), postId);
        if (updated == 0) throw new IllegalArgumentException("Only review drafts can be revised");
        if (!sourceIds.isEmpty()) {
            jdbc.update("UPDATE collected_items SET used_in_post_id=? WHERE id=ANY(?) AND (used_in_post_id IS NULL OR used_in_post_id=?)",
                    postId, sourceIds.toArray(UUID[]::new), postId);
        }
        storeRevision(postId, version, c, sourceIds, instruction, score, actor);
        return version;
    }
    @Override @Transactional
    public void restoreRevision(UUID postId, int version) {
        Revision r = revisions(postId).stream().filter(v -> v.version() == version).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Revision not found"));
        AiWriterPort.Completion c = new AiWriterPort.Completion(r.title(), r.body(), r.excerpt(), r.seoTitle(),
                r.seoDescription(), r.seoKeywords(), r.sourceIds(), r.model(), 0, 0, 0);
        appendRevision(postId, c, r.sourceIds(), "Restore revision " + version,
                r.qualityScore() == null ? 0 : r.qualityScore(), null);
    }
    @Override public List<Revision> revisions(UUID postId){return jdbc.query("SELECT id,version,title,body,excerpt,seo_title,seo_description,seo_keywords,instruction,source_ids,model,quality_score,tokens_used,cost_usd,created_at FROM blog_draft_revisions WHERE post_id=? ORDER BY version",(rs,n)->new Revision(rs.getObject(1,UUID.class),rs.getInt(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),Arrays.asList((String[])rs.getArray(8).getArray()),rs.getString(9),Arrays.asList((UUID[])rs.getArray(10).getArray()),rs.getString(11),(Integer)rs.getObject(12),(Integer)rs.getObject(13),rs.getBigDecimal(14)==null?null:rs.getBigDecimal(14).doubleValue(),rs.getTimestamp(15).toInstant()),postId);}
    @Override public List<BlogPost> reviewQueue(int limit){return jdbc.query("SELECT "+COLUMNS+" FROM blog_posts WHERE status='REVIEW' ORDER BY created_at ASC LIMIT ?",this::map,Math.max(1,Math.min(100,limit)));}
    @Override @Transactional public void reject(UUID postId){int n=jdbc.update("UPDATE blog_posts SET status='ARCHIVED' WHERE id=? AND status='REVIEW'",postId);if(n==0)throw new IllegalArgumentException("Only review drafts can be rejected");}
    @Override public Optional<BlogPost> findPost(UUID postId){return jdbc.query("SELECT "+COLUMNS+" FROM blog_posts WHERE id=?",this::map,postId).stream().findFirst();}
    @Override public UUID startRun(UUID queueId,String summary){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO agent_runs(id,agent_type,queue_id,status,input_summary) VALUES(?,'WRITER',?,'RUNNING',?)",id,queueId,summary);return id;}
    @Override public UUID startRevisionRun(UUID actorId,String summary){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO agent_runs(id,agent_type,status,input_summary) VALUES(?,'WRITER','RUNNING',?)",id,summary+"; actor="+actorId);return id;}
    @Override public void finishRevisionRun(UUID runId,boolean success,int tokens,double cost,String error){jdbc.update("UPDATE agent_runs SET status=?,finished_at=NOW(),tokens_used=?,cost_usd=?,error_message=? WHERE id=?",success?"SUCCESS":"FAILED",tokens,cost,error,runId);}
    @Override public Stats stats(){return jdbc.queryForObject("SELECT (SELECT count(*) FROM blog_posts WHERE status='REVIEW'),(SELECT count(*) FROM agent_runs WHERE agent_type='WRITER' AND started_at>=date_trunc('day',NOW())),(SELECT COALESCE(sum(tokens_used),0) FROM agent_runs WHERE agent_type='WRITER' AND started_at>=date_trunc('day',NOW())),(SELECT COALESCE(sum(cost_usd),0) FROM agent_runs WHERE agent_type='WRITER' AND started_at>=date_trunc('day',NOW())),(SELECT COALESCE(sum(cost_usd),0) FROM agent_runs WHERE agent_type='WRITER' AND started_at>=date_trunc('month',NOW())),(SELECT count(*) FROM blog_generation_queue WHERE status='FAILED')",(rs,n)->new Stats(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getBigDecimal(4).doubleValue(),rs.getBigDecimal(5).doubleValue(),rs.getLong(6)));}
    private void storeRevision(UUID postId,int version,AiWriterPort.Completion c,List<UUID> ids,String instruction,int score,UUID actor){
        jdbc.update(connection->{var ps=connection.prepareStatement("INSERT INTO blog_draft_revisions(post_id,version,title,body,excerpt,seo_title,seo_description,seo_keywords,source_ids,instruction,model,quality_score,tokens_used,cost_usd,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");ps.setObject(1,postId);ps.setInt(2,version);ps.setString(3,c.title());ps.setString(4,c.bodyHtml());ps.setString(5,c.excerpt());ps.setString(6,c.seoTitle());ps.setString(7,c.seoDescription());ps.setArray(8,connection.createArrayOf("text",c.seoKeywords().toArray()));ps.setArray(9,connection.createArrayOf("uuid",ids.toArray()));ps.setString(10,instruction);ps.setString(11,c.model());ps.setInt(12,score);ps.setInt(13,c.tokensUsed());ps.setBigDecimal(14,java.math.BigDecimal.valueOf(c.costUsd()));ps.setObject(15,actor);return ps;});
    }
    private BlogPost map(java.sql.ResultSet rs,int row)throws java.sql.SQLException{return new BlogPost(rs.getObject("id",UUID.class),rs.getObject("author_id",UUID.class),rs.getString("author_type"),rs.getString("title"),rs.getString("slug"),rs.getString("body"),rs.getString("excerpt"),rs.getObject("source_module_id",UUID.class),rs.getObject("source_question_id",UUID.class),rs.getString("status"),rs.getTimestamp("published_at")==null?null:rs.getTimestamp("published_at").toInstant(),rs.getInt("view_count"),rs.getInt("like_count"),Arrays.asList((String[])rs.getArray("tags").getArray()),rs.getTimestamp("created_at").toInstant());}
    private static String slug(String title){String s=java.text.Normalizer.normalize(title,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");if(s.isBlank())s="ai-article";return s.substring(0,Math.min(260,s.length()))+"-"+UUID.randomUUID().toString().substring(0,8);}
}
