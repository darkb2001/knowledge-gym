package com.knowledgegym.agent;

import com.knowledgegym.blog.application.CollectItemsUseCase;
import com.knowledgegym.blog.domain.port.KnowledgeIntakePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

/**
 * Bounded intake worker: collects only the goal's allowlisted HTTPS domains and leaves
 * the result in REVIEW. It never publishes, changes users, or changes learning scores.
 */
@Component
@ConditionalOnProperty(name="app.blog.knowledge-intake.enabled", havingValue="true")
public class KnowledgeIntakeWorker {
    private static final Logger log=LoggerFactory.getLogger(KnowledgeIntakeWorker.class);
    private final KnowledgeIntakePort intake; private final CollectItemsUseCase collector;
    private final com.knowledgegym.blog.domain.port.BlogScheduleSettingsPort writerSettings;
    public KnowledgeIntakeWorker(KnowledgeIntakePort intake, CollectItemsUseCase collector,
                                 com.knowledgegym.blog.domain.port.BlogScheduleSettingsPort writerSettings){this.intake=intake;this.collector=collector;this.writerSettings=writerSettings;}
    @Scheduled(fixedDelayString="${app.blog.knowledge-intake.poll-ms:30000}",initialDelayString="${app.blog.knowledge-intake.initial-delay-ms:20000}")
    public void runOne(){
        intake.claimNextRun().ifPresent(run -> {
            try {
                var goal=intake.getGoal(run.goalId());
                if(goal.status()!=KnowledgeIntakePort.GoalStatus.ACTIVE){intake.finishRun(run.id(),KnowledgeIntakePort.RunStatus.CANCELLED,0,0,0,BigDecimal.ZERO,"Goal is not active");return;}
                var result=collector.executeForDomains(goal.allowedDomains());
                // Collected items are source material. A separate writer/review action creates learning/blog drafts.
                if (result.itemsInserted() > 0 && goal.autoPublish()) {
                    // Idempotent request key: retries cannot enqueue duplicate writer jobs. A goal with
                    // autoPublish=false remains source material for explicit admin review.
                    writerSettings.enqueueNow(goal.topic(),run.requestedBy(),"knowledge-intake:"+run.id());
                }
                intake.finishRun(run.id(),KnowledgeIntakePort.RunStatus.REVIEW,result.itemsInserted(),result.itemsInserted(),result.failedSources(),BigDecimal.ZERO,null);
                log.info("Knowledge intake run {} ready for review: {} items",run.id(),result.itemsInserted());
            } catch(Exception e){intake.finishRun(run.id(),KnowledgeIntakePort.RunStatus.FAILED,0,0,0,BigDecimal.ZERO,safe(e));log.warn("Knowledge intake run {} failed",run.id(),e);}
        });
    }
    private static String safe(Exception e){String m=e.getMessage();return m==null?e.getClass().getSimpleName():m.substring(0,Math.min(500,m.length()));}
}
