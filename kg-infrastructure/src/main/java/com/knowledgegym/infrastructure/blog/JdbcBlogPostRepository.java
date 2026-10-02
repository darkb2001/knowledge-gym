package com.knowledgegym.infrastructure.blog;

import tools.jackson.databind.ObjectMapper;
import com.knowledgegym.blog.domain.model.BlogPost;
import com.knowledgegym.blog.domain.port.BlogPostRepository;
import com.knowledgegym.shared.application.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcBlogPostRepository implements BlogPostRepository {
    private static final String COLUMNS = "id,author_id,author_type,title,slug,body,excerpt,source_module_id,source_question_id,status,published_at,view_count,like_count,tags,created_at";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final String canonicalBaseUrl;

    public JdbcBlogPostRepository(JdbcTemplate jdbc, ObjectMapper json,
                                  @Value("${app.blog.canonical-base-url:http://localhost:3000}") String canonicalBaseUrl) {
        this.jdbc = jdbc;
        this.json = json;
        this.canonicalBaseUrl = canonicalBaseUrl.replaceAll("/$", "");
    }

    @Override public List<BlogPost> findPublished(int offset, int limit, String tag) {
        return jdbc.query("SELECT " + COLUMNS + " FROM blog_posts WHERE status='PUBLISHED' AND (?::text IS NULL OR ?=ANY(tags)) ORDER BY published_at DESC OFFSET ? LIMIT ?",
                this::map, tag, tag, offset, limit);
    }
    @Override public Optional<BlogPost> findPublishedBySlug(String slug) {
        return jdbc.query("SELECT " + COLUMNS + " FROM blog_posts WHERE slug=? AND status='PUBLISHED'", this::map, slug).stream().findFirst();
    }
    @Override public Optional<BlogPost> findById(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM blog_posts WHERE id=?", this::map, id).stream().findFirst();
    }

    @Override @Transactional
    public BlogPost createDraft(UUID authorId, String title, String slug, String body, String excerpt,
                                UUID moduleId, UUID questionId, List<String> tags) {
        UUID id = UUID.randomUUID();
        jdbc.update((PreparedStatementCreator) connection -> {
            var ps = connection.prepareStatement("INSERT INTO blog_posts(id,author_id,author_type,title,slug,body,excerpt,source_module_id,source_question_id,status,tags) VALUES(?,?,'HUMAN',?,?,?,?,?,?,'DRAFT',?)");
            ps.setObject(1,id); ps.setObject(2,authorId); ps.setString(3,title); ps.setString(4,slug); ps.setString(5,body); ps.setString(6,excerpt);
            ps.setObject(7,moduleId); ps.setObject(8,questionId); ps.setArray(9,connection.createArrayOf("text",tags.toArray())); return ps;
        });
        return findById(id).orElseThrow();
    }

    @Override @Transactional
    public void publish(UUID postId) {
        int changed = jdbc.update("UPDATE blog_posts SET status='PUBLISHED',published_at=COALESCE(published_at,NOW()) WHERE id=? AND status IN ('DRAFT','REVIEW')", postId);
        if (changed == 0) throw new IllegalArgumentException("Bài viết không còn ở trạng thái có thể publish");
        BlogPost post = findById(postId).orElseThrow();
        try {
            String payload = json.writeValueAsString(Map.of(
                    "postId", post.id(),
                    "slug", post.slug(),
                    "title", post.title(),
                    "excerpt", post.excerpt() == null ? "" : post.excerpt(),
                    "canonicalUrl", canonicalBaseUrl + "/blog/" + post.slug()));
            jdbc.update("INSERT INTO event_outbox(aggregate_type,aggregate_id,event_type,payload) VALUES('blog_post',?,'blog.published.v1',?::jsonb)", postId, payload);
        } catch (Exception e) { throw new IllegalStateException("Could not write blog publish outbox event", e); }
    }

    @Override public List<Comment> comments(UUID postId) {
        return jdbc.query("SELECT id,post_id,user_id,parent_id,content,created_at FROM blog_comments WHERE post_id=? ORDER BY created_at ASC",
                (rs,n)->new Comment(rs.getObject("id",UUID.class),rs.getObject("post_id",UUID.class),rs.getObject("user_id",UUID.class),rs.getObject("parent_id",UUID.class),rs.getString("content"),rs.getTimestamp("created_at").toInstant()),postId);
    }
    @Override @Transactional
    public Comment addComment(UUID postId, UUID userId, UUID parentId, String body) {
        if (parentId != null && jdbc.queryForObject("SELECT count(*) FROM blog_comments WHERE id=? AND post_id=?",Long.class,parentId,postId)==0)
            throw new IllegalArgumentException("parentId không thuộc bài viết này");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO blog_comments(id,post_id,user_id,parent_id,content) VALUES(?,?,?,?,?)",id,postId,userId,parentId,body);
        return new Comment(id,postId,userId,parentId,body,java.time.Instant.now());
    }
    @Override @Transactional
    public boolean setLiked(UUID postId, UUID userId, boolean liked) {
        if(liked){
            int inserted=jdbc.update("INSERT INTO blog_post_likes(post_id,user_id) VALUES(?,?) ON CONFLICT DO NOTHING",postId,userId);
            if(inserted>0)jdbc.update("UPDATE blog_posts SET like_count=like_count+1 WHERE id=?",postId);
            return true;
        }
        int deleted=jdbc.update("DELETE FROM blog_post_likes WHERE post_id=? AND user_id=?",postId,userId);
        if(deleted>0)jdbc.update("UPDATE blog_posts SET like_count=GREATEST(0,like_count-1) WHERE id=?",postId);
        return false;
    }
    @Override @Transactional
    public void recordView(UUID postId, UUID userId) {
        int inserted=jdbc.update("INSERT INTO blog_views(post_id,user_id) VALUES(?,?) ON CONFLICT DO NOTHING",postId,userId);
        if(inserted>0)jdbc.update("UPDATE blog_posts SET view_count=view_count+1 WHERE id=?",postId);
    }
    private BlogPost map(java.sql.ResultSet rs,int row) throws java.sql.SQLException {
        return new BlogPost(rs.getObject("id",UUID.class),rs.getObject("author_id",UUID.class),rs.getString("author_type"),rs.getString("title"),rs.getString("slug"),rs.getString("body"),rs.getString("excerpt"),rs.getObject("source_module_id",UUID.class),rs.getObject("source_question_id",UUID.class),rs.getString("status"),rs.getTimestamp("published_at")==null?null:rs.getTimestamp("published_at").toInstant(),rs.getInt("view_count"),rs.getInt("like_count"),java.util.Arrays.asList((String[])rs.getArray("tags").getArray()),rs.getTimestamp("created_at").toInstant());
    }
}
