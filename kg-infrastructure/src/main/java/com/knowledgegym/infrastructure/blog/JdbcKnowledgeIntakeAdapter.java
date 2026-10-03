package com.knowledgegym.infrastructure.blog;

import com.knowledgegym.blog.domain.port.KnowledgeIntakePort;
import com.knowledgegym.shared.application.NotFoundException;
import com.knowledgegym.shared.domain.model.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Component
public class JdbcKnowledgeIntakeAdapter implements KnowledgeIntakePort {
    private final JdbcTemplate jdbc;
    public JdbcKnowledgeIntakeAdapter(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static Goal goal(java.sql.ResultSet r) throws java.sql.SQLException { return new Goal(r.getObject("id",UUID.class),r.getString("name"),r.getString("topic"),r.getString("objective"),GoalStatus.valueOf(r.getString("status")),r.getBoolean("auto_publish"),r.getInt("daily_item_limit"),r.getBigDecimal("daily_cost_limit_usd"),r.getString("schedule_cron"),Arrays.asList((String[])r.getArray("allowed_domains").getArray()),r.getObject("created_by",UUID.class),r.getTimestamp("updated_at").toInstant()); }
    private static Run run(java.sql.ResultSet r) throws java.sql.SQLException { Timestamp started=r.getTimestamp("started_at"),finished=r.getTimestamp("finished_at"); return new Run(r.getObject("id",UUID.class),r.getObject("goal_id",UUID.class),RunStatus.valueOf(r.getString("status")),r.getInt("discovered_count"),r.getInt("accepted_count"),r.getInt("rejected_count"),r.getBigDecimal("cost_usd"),r.getString("error_message"),r.getObject("requested_by",UUID.class),r.getTimestamp("created_at").toInstant(),started==null?null:started.toInstant(),finished==null?null:finished.toInstant()); }
    @Override @Transactional public Goal create(UUID actor,String name,String topic,String objective,boolean auto,int limit,BigDecimal cost,String cron,List<String> domains){
        UUID id=UUID.randomUUID(); jdbc.update(c-> {var p=c.prepareStatement("INSERT INTO knowledge_goals(id,name,topic,objective,auto_publish,daily_item_limit,daily_cost_limit_usd,schedule_cron,allowed_domains,created_by) VALUES(?,?,?,?,?,?,?,?,?,?)");p.setObject(1,id);p.setString(2,name);p.setString(3,topic);p.setString(4,objective);p.setBoolean(5,auto);p.setInt(6,limit);p.setBigDecimal(7,cost);p.setString(8,cron);p.setArray(9,c.createArrayOf("text",domains.toArray()));p.setObject(10,actor);return p;}); audit(actor,id,"KNOWLEDGE_GOAL","CREATE","created"); return getGoal(id); }
    @Override public Goal getGoal(UUID id){return jdbc.query("SELECT * FROM knowledge_goals WHERE id=?",(r,n)->goal(r),id).stream().findFirst().orElseThrow(()->new NotFoundException("Knowledge goal không tồn tại"));}
    @Override public List<Goal> listGoals(GoalStatus status){String sql="SELECT * FROM knowledge_goals"+(status==null?"":" WHERE status=?")+" ORDER BY updated_at DESC,id DESC";return status==null?jdbc.query(sql,(r,n)->goal(r)):jdbc.query(sql,(r,n)->goal(r),status.name());}
    @Override @Transactional public Goal updateStatus(UUID actor,UUID id,GoalStatus status,String reason){getGoal(id);jdbc.update("UPDATE knowledge_goals SET status=?,updated_at=now() WHERE id=?",status.name(),id);audit(actor,id,"KNOWLEDGE_GOAL","STATUS_"+status.name(),reason);return getGoal(id);}
    @Override @Transactional public Run queueRun(UUID actor,UUID goalId,String reason){getGoal(goalId);UUID id=UUID.randomUUID();jdbc.update("INSERT INTO knowledge_intake_runs(id,goal_id,status,requested_by) VALUES(?,?, 'QUEUED',?)",id,goalId,actor);audit(actor,id,"KNOWLEDGE_INTAKE_RUN","QUEUE",reason);return jdbc.query("SELECT * FROM knowledge_intake_runs WHERE id=?",(r,n)->run(r),id).getFirst();}
    @Override @Transactional public Optional<Run> claimNextRun(){
        return jdbc.query("SELECT * FROM knowledge_intake_runs WHERE status='QUEUED' ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",(r,n)->run(r)).stream().findFirst().map(value->{jdbc.update("UPDATE knowledge_intake_runs SET status='RUNNING',started_at=now() WHERE id=?",value.id());return new Run(value.id(),value.goalId(),RunStatus.RUNNING,value.discoveredCount(),value.acceptedCount(),value.rejectedCount(),value.costUsd(),value.errorMessage(),value.requestedBy(),value.createdAt(),java.time.Instant.now(),value.finishedAt());});
    }
    @Override public void finishRun(UUID runId,RunStatus status,int discovered,int accepted,int rejected,BigDecimal cost,String error){jdbc.update("UPDATE knowledge_intake_runs SET status=?,discovered_count=?,accepted_count=?,rejected_count=?,cost_usd=?,error_message=?,finished_at=now() WHERE id=?",status.name(),discovered,accepted,rejected,cost,error,runId);}
    @Override public com.knowledgegym.shared.domain.model.PageResult<Run> listRuns(UUID goalId,int page,int size){
        long total=jdbc.queryForObject("SELECT count(*) FROM knowledge_intake_runs WHERE goal_id=?",Long.class,goalId);
        var items=jdbc.query("SELECT * FROM knowledge_intake_runs WHERE goal_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",(r,n)->run(r),goalId,size,(long)(page-1)*size);
        return new com.knowledgegym.shared.domain.model.PageResult<>(items,page,size,total);
    }
    @Override public void audit(UUID actor,UUID id,String type,String action,String reason){jdbc.update("INSERT INTO knowledge_intake_audit(entity_id,entity_type,actor_id,action,details) VALUES(?,?,?,?,jsonb_build_object('reason',CAST(? AS text)))",id,type,actor,action,reason);}
}
