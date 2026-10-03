package com.knowledgegym.infrastructure.blog;

import com.knowledgegym.blog.domain.port.LearningContentDraftPort;
import com.knowledgegym.shared.application.NotFoundException;
import com.knowledgegym.shared.domain.model.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class JdbcLearningContentDraftAdapter implements LearningContentDraftPort {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public JdbcLearningContentDraftAdapter(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    private static Draft map(java.sql.ResultSet r)throws java.sql.SQLException{Timestamp reviewed=r.getTimestamp("reviewed_at");return new Draft(r.getObject("id",UUID.class),r.getObject("goal_id",UUID.class),Kind.valueOf(r.getString("kind")),r.getString("title"),r.getString("payload"),Arrays.asList((UUID[])r.getArray("source_ids").getArray()),Status.valueOf(r.getString("status")),r.getString("model"),r.getInt("tokens_used"),r.getBigDecimal("cost_usd"),r.getTimestamp("created_at").toInstant(),reviewed==null?null:reviewed.toInstant(),r.getString("review_reason"));}
    @Override public PageResult<Draft> list(Status status,Kind kind,int page,int size){StringBuilder w=new StringBuilder(" WHERE 1=1");var args=new java.util.ArrayList<>();if(status!=null){w.append(" AND status=?");args.add(status.name());}if(kind!=null){w.append(" AND kind=?");args.add(kind.name());}long total=jdbc.queryForObject("SELECT count(*) FROM learning_content_drafts"+w,args.toArray(),Long.class);args.add(size);args.add((long)(page-1)*size);var rows=jdbc.query("SELECT * FROM learning_content_drafts"+w+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",(r,n)->map(r),args.toArray());return new PageResult<>(rows,page,size,total);}
    @Override @Transactional public Draft save(UUID actor,UUID goalId,Generated g){UUID id=UUID.randomUUID();jdbc.update(c->{var p=c.prepareStatement("INSERT INTO learning_content_drafts(id,goal_id,kind,title,payload,source_ids,model,tokens_used,cost_usd) VALUES(?,?,?,?,?::jsonb,?,?,?,?)");p.setObject(1,id);p.setObject(2,goalId);p.setString(3,g.kind().name());p.setString(4,g.title());p.setString(5,g.payloadJson());p.setArray(6,c.createArrayOf("uuid",g.sourceIds().toArray()));p.setString(7,g.model());p.setInt(8,g.tokensUsed());p.setBigDecimal(9,g.costUsd());return p;});audit(actor,id,"CREATE","AI generated learning draft");return get(id);}
    @Override public Draft get(UUID id){return jdbc.query("SELECT * FROM learning_content_drafts WHERE id=?",(r,n)->map(r),id).stream().findFirst().orElseThrow(()->new NotFoundException("Learning draft không tồn tại"));}
    @Override @Transactional public Draft review(UUID actor,UUID id,Status status,String reason){if(status==null||status==Status.REVIEW||actor==null||reason==null||reason.isBlank()||reason.length()>500)throw new IllegalArgumentException("Invalid review");get(id);int changed=jdbc.update("UPDATE learning_content_drafts SET status=?,reviewer_id=?,review_reason=?,reviewed_at=now() WHERE id=? AND status='REVIEW'",status.name(),actor,reason,id);if(changed!=1)throw new com.knowledgegym.shared.application.ConflictException("Draft đã được review");audit(actor,id,status.name(),reason);return get(id);}
    @Override public List<Evidence> evidence(List<UUID> ids){if(ids==null||ids.isEmpty())return List.of();var placeholders=String.join(",",java.util.Collections.nCopies(ids.size(),"?"));return jdbc.query("SELECT id,title,summary,url FROM collected_items WHERE id IN ("+placeholders+")",(r,n)->new Evidence(r.getObject("id",UUID.class),r.getString("title"),r.getString("summary"),r.getString("url")),ids.toArray());}
    @Override public void audit(UUID actor,UUID id,String action,String reason){jdbc.update("INSERT INTO learning_draft_audit(draft_id,actor_id,action,details) VALUES(?,?,?,jsonb_build_object('reason',CAST(? AS text)))",id,actor,action,reason);}
}
