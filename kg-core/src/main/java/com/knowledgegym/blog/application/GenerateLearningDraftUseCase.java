package com.knowledgegym.blog.application;

import com.knowledgegym.blog.domain.port.LearningContentAiPort;
import com.knowledgegym.blog.domain.port.LearningContentDraftPort;
import com.knowledgegym.blog.domain.port.LearningDraftMaterializerPort;
import com.knowledgegym.shared.application.NotFoundException;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

public class GenerateLearningDraftUseCase {
    private final LearningContentDraftPort drafts;
    private final LearningContentAiPort ai;
    private final LearningDraftMaterializerPort materializer;
    public GenerateLearningDraftUseCase(LearningContentDraftPort drafts, LearningContentAiPort ai, LearningDraftMaterializerPort materializer){this.drafts=drafts;this.ai=ai;this.materializer=materializer;}
    @Transactional
    public LearningContentDraftPort.Draft generate(UUID actor, UUID goalId, LearningContentDraftPort.Kind kind,
                                                    String topic, String instruction, List<UUID> sourceIds){
        if(actor==null||kind==null||topic==null||topic.isBlank()||topic.length()>200)throw new IllegalArgumentException("kind/topic bắt buộc");
        if(instruction==null||instruction.isBlank()||instruction.length()>5000)throw new IllegalArgumentException("instruction bắt buộc, tối đa 5000 ký tự");
        if(sourceIds==null||sourceIds.isEmpty()||sourceIds.size()>20)throw new IllegalArgumentException("sourceIds phải từ 1 đến 20");
        var evidence=drafts.evidence(sourceIds);
        if(evidence.size()!=sourceIds.stream().distinct().count())throw new NotFoundException("Một hoặc nhiều source không tồn tại");
        var generated=ai.generate(kind,topic,instruction,evidence);
        if(generated.payloadJson()==null||generated.payloadJson().isBlank()||generated.title()==null||generated.title().isBlank())throw new IllegalStateException("AI trả về draft rỗng");
        if(!generated.sourceIds().stream().allMatch(sourceIds::contains))throw new IllegalStateException("AI tham chiếu source ngoài evidence");
        return drafts.save(actor,goalId,generated);
    }
    public LearningContentDraftPort.Draft get(UUID id){return drafts.get(id);}
    public com.knowledgegym.shared.domain.model.PageResult<LearningContentDraftPort.Draft> list(LearningContentDraftPort.Status status,LearningContentDraftPort.Kind kind,int page,int size){
        if(page<1||size<1||size>100)throw new IllegalArgumentException("page >= 1, size 1..100");return drafts.list(status,kind,page,size);
    }
    @Transactional public UUID materializeQuestion(UUID actor, UUID id, UUID moduleId, String reason){ return materialize(actor,id,moduleId,reason,LearningContentDraftPort.Kind.QUESTION); }
    @Transactional public UUID materialize(UUID actor, UUID id, UUID moduleId, String reason, LearningContentDraftPort.Kind kind){
        if(moduleId==null||reason==null||reason.isBlank()||reason.length()>500)throw new IllegalArgumentException("moduleId/reason bắt buộc");
        var draft=drafts.get(id); if(draft.status()!=LearningContentDraftPort.Status.APPROVED)throw new IllegalArgumentException("Draft phải được APPROVED trước");
        if(draft.kind()!=kind)throw new IllegalArgumentException("Draft kind không khớp endpoint");
        return materializer.materialize(id,moduleId,actor,reason.trim());
    }
    @Transactional public UUID rollback(UUID actor,UUID id,String reason){return materializer.rollback(id,actor,reason);}
    @Transactional public LearningContentDraftPort.Draft review(UUID actor,UUID id,LearningContentDraftPort.Status status,String reason){
        if(status==null||status==LearningContentDraftPort.Status.REVIEW||reason==null||reason.isBlank()||reason.length()>500)throw new IllegalArgumentException("review status/reason không hợp lệ");
        var draft=drafts.get(id);if(draft.status()!=LearningContentDraftPort.Status.REVIEW)throw new IllegalArgumentException("Draft đã review");return drafts.review(actor,id,status,reason.trim());
    }
}
