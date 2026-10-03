package com.knowledgegym.blog.application;

import com.knowledgegym.blog.domain.port.KnowledgeIntakePort;
import com.knowledgegym.shared.application.NotFoundException;
import com.knowledgegym.shared.domain.model.PageResult;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ManageKnowledgeIntakeUseCase {
    private final KnowledgeIntakePort intake;
    public ManageKnowledgeIntakeUseCase(KnowledgeIntakePort intake) { this.intake = intake; }
    public KnowledgeIntakePort.Goal create(UUID actor, String name, String topic, String objective,
            boolean autoPublish, int itemLimit, BigDecimal costLimit, String cron, List<String> domains) {
        if (actor == null || blank(name) || blank(topic) || blank(objective)) throw new IllegalArgumentException("name/topic/objective bắt buộc");
        if (name.length()>200 || topic.length()>200 || objective.length()>10_000) throw new IllegalArgumentException("goal vượt giới hạn");
        if (itemLimit < 1 || itemLimit > 200 || costLimit == null || costLimit.signum() < 0 || costLimit.compareTo(BigDecimal.valueOf(10_000)) > 0)
            throw new IllegalArgumentException("itemLimit 1..200, costLimit 0..10000");
        if (cron != null && cron.length() > 100) throw new IllegalArgumentException("scheduleCron vượt giới hạn");
        var safeDomains = (domains == null ? List.<String>of() : domains).stream().map(ManageKnowledgeIntakeUseCase::domain).distinct().limit(50).toList();
        if (safeDomains.isEmpty()) throw new IllegalArgumentException("Phải khai báo ít nhất một domain nguồn");
        return intake.create(actor,name.trim(),topic.trim(),objective.trim(),autoPublish,itemLimit,costLimit,cron,safeDomains);
    }
    public List<KnowledgeIntakePort.Goal> goals(KnowledgeIntakePort.GoalStatus status) { return intake.listGoals(status); }
    public KnowledgeIntakePort.Goal goal(UUID id) { return intake.getGoal(id); }
    @Transactional public KnowledgeIntakePort.Goal status(UUID actor, UUID id, KnowledgeIntakePort.GoalStatus status, String reason) {
        reason(reason); if (status == null) throw new IllegalArgumentException("status bắt buộc");
        var goal = intake.getGoal(id); if (goal.status() == KnowledgeIntakePort.GoalStatus.ARCHIVED && status != KnowledgeIntakePort.GoalStatus.ARCHIVED)
            throw new IllegalArgumentException("Goal đã archive không thể bật lại");
        var result = intake.updateStatus(actor,id,status,reason.trim());
        return result;
    }
    @Transactional public KnowledgeIntakePort.Run run(UUID actor, UUID goalId, String reason) {
        reason(reason); var goal=intake.getGoal(goalId);
        if (goal.status() != KnowledgeIntakePort.GoalStatus.ACTIVE) throw new IllegalArgumentException("Chỉ goal ACTIVE mới được chạy");
        return intake.queueRun(actor,goalId,reason.trim());
    }
    public PageResult<KnowledgeIntakePort.Run> runs(UUID goalId,int page,int size) {
        if (goalId==null || page<1 || size<1 || size>100) throw new IllegalArgumentException("goalId/page/size không hợp lệ");
        return intake.listRuns(goalId,page,size);
    }
    private static void reason(String s){if(blank(s)||s.length()>500)throw new IllegalArgumentException("reason bắt buộc, tối đa 500 ký tự");}
    private static boolean blank(String s){return s==null||s.isBlank();}
    private static String domain(String value){
        if(value==null||value.isBlank()) throw new IllegalArgumentException("domain rỗng");
        String raw=value.trim().toLowerCase(Locale.ROOT);
        URI uri; try { uri=URI.create(raw.contains("://")?raw:"https://"+raw); } catch(Exception e){throw new IllegalArgumentException("domain không hợp lệ");}
        if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getPort()!=-1 || !List.of("https").contains(uri.getScheme())) throw new IllegalArgumentException("source chỉ cho HTTPS domain");
        String host=uri.getHost().toLowerCase(Locale.ROOT);
        if(host.equals("localhost")||host.endsWith(".localhost")||host.equals("127.0.0.1")||host.startsWith("10.")||host.startsWith("192.168.")||host.startsWith("172.16.")) throw new IllegalArgumentException("domain nội bộ không được phép");
        return host;
    }
}
