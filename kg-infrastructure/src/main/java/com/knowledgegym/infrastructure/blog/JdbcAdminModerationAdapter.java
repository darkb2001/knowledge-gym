package com.knowledgegym.infrastructure.blog;

import com.knowledgegym.blog.domain.port.AdminModerationPort;
import com.knowledgegym.shared.application.ConflictException;
import com.knowledgegym.shared.application.NotFoundException;
import com.knowledgegym.shared.domain.model.PageResult;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class JdbcAdminModerationAdapter implements AdminModerationPort {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcAdminModerationAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final String SELECT = "SELECT c.*,p.title AS post_title,u.display_name FROM blog_comments c "
            + "JOIN blog_posts p ON p.id=c.post_id JOIN users u ON u.id=c.user_id";
    private static final RowMapper<CommentView> MAPPER = (rs,n) -> new CommentView(
            rs.getObject("id",UUID.class), rs.getObject("post_id",UUID.class), rs.getString("post_title"),
            rs.getObject("user_id",UUID.class), rs.getString("display_name"), rs.getObject("parent_id",UUID.class),
            rs.getString("content"), CommentStatus.valueOf(rs.getString("status")), rs.getTimestamp("created_at").toInstant());
    @Override public PageResult<CommentView> comments(String q, UUID postId, UUID userId,
            CommentStatus status, int page, int size) {
        var p = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (q != null && !q.isBlank()) { where.append(" AND strpos(lower(c.content),:q)>0"); p.addValue("q",q.trim().toLowerCase(java.util.Locale.ROOT)); }
        if (postId != null) { where.append(" AND c.post_id=:post"); p.addValue("post",postId); }
        if (userId != null) { where.append(" AND c.user_id=:user"); p.addValue("user",userId); }
        if (status != null) { where.append(" AND c.status=:status"); p.addValue("status",status.name()); }
        long total = jdbc.queryForObject("SELECT count(*) FROM blog_comments c" + where,p,Long.class);
        p.addValue("limit",size).addValue("offset",(long)(page-1)*size);
        return new PageResult<>(jdbc.query(SELECT + where + " ORDER BY c.created_at DESC,c.id DESC LIMIT :limit OFFSET :offset",p,MAPPER),page,size,total);
    }
    @Override public CommentView moderateComment(UUID actor, UUID id, CommentStatus status, boolean restore, String reason) {
        var p = new MapSqlParameterSource("id",id);
        var old = jdbc.query("SELECT status FROM blog_comments WHERE id=:id FOR UPDATE",p,
                (rs,n)->CommentStatus.valueOf(rs.getString(1))).stream().findFirst()
                .orElseThrow(()->new NotFoundException("Bình luận không tồn tại"));
        if (restore && (old != CommentStatus.DELETED || status != CommentStatus.HIDDEN))
            throw new ConflictException("Chỉ khôi phục bình luận đã xóa về trạng thái HIDDEN");
        if (!restore && old == CommentStatus.DELETED && status != CommentStatus.DELETED)
            throw new ConflictException("Cần khôi phục bình luận trước khi hiển thị");
        if (old != status) {
            jdbc.update("UPDATE blog_comments SET status=:status,updated_at=now() WHERE id=:id",p.addValue("status",status.name()));
            audit(actor,id,"BLOG_COMMENT","COMMENT_" + (restore ? "RESTORE" : status.name()),old.name(),status.name(),reason);
        }
        return jdbc.query(SELECT + " WHERE c.id=:id",p,MAPPER).getFirst();
    }
    @Override public String moderatePost(UUID actor, UUID id, PostAction action, String reason) {
        var p = new MapSqlParameterSource("id",id);
        String old = jdbc.query("SELECT status FROM blog_posts WHERE id=:id FOR UPDATE",p,(rs,n)->rs.getString(1))
                .stream().findFirst().orElseThrow(()->new NotFoundException("Bài viết không tồn tại"));
        String next = switch(action) {
            case HIDE -> "HIDDEN";
            case ARCHIVE -> "ARCHIVED";
            case DELETE -> "DELETED";
            // Restore never silently republishes; use existing publish API afterwards.
            case RESTORE -> "REVIEW";
        };
        if (action == PostAction.RESTORE && !java.util.Set.of("HIDDEN","ARCHIVED","DELETED").contains(old))
            throw new ConflictException("Bài viết không ở trạng thái có thể khôi phục");
        if (old.equals("DELETED") && action != PostAction.RESTORE && action != PostAction.DELETE)
            throw new ConflictException("Cần khôi phục bài viết trước");
        if (!old.equals(next)) {
            jdbc.update("UPDATE blog_posts SET status=:status,updated_at=now() WHERE id=:id",p.addValue("status",next));
            audit(actor,id,"BLOG_POST","POST_" + action.name(),old,next,reason);
        }
        return next;
    }
    private void audit(UUID actor, UUID id, String type, String action, String old, String next, String reason) {
        jdbc.update("""
                INSERT INTO audit_logs(user_id,action,entity_type,entity_id,details)
                VALUES(:actor,:action,:type,:id,jsonb_build_object('reason',CAST(:reason AS text),
                'before',CAST(:old AS text),'after',CAST(:next AS text)))
                """,new MapSqlParameterSource("actor",actor).addValue("id",id).addValue("type",type)
                .addValue("action",action).addValue("old",old).addValue("next",next).addValue("reason",reason));
    }
}
