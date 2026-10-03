package com.knowledgegym.blog.application;

import com.knowledgegym.blog.domain.port.KnowledgeIntakePort;
import com.knowledgegym.shared.application.ConflictException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManageKnowledgeIntakeUseCaseTest {
    private final KnowledgeIntakePort port=mock(KnowledgeIntakePort.class);
    private final ManageKnowledgeIntakeUseCase use=new ManageKnowledgeIntakeUseCase(port);
    private final UUID actor=UUID.randomUUID(),goal=UUID.randomUUID();
    private KnowledgeIntakePort.Goal active(){return new KnowledgeIntakePort.Goal(goal,"Java","Java","update",KnowledgeIntakePort.GoalStatus.ACTIVE,false,20,BigDecimal.TEN,null,List.of("docs.oracle.com"),actor,Instant.EPOCH);}
    @Test void createsGoalWithHttpsDomainAndRejectsPrivateNetwork(){
        use.create(actor,"Java updates","Java","Collect official updates",false,20,BigDecimal.TEN,null,List.of("https://docs.oracle.com"));
        verify(port).create(actor,"Java updates","Java","Collect official updates",false,20,BigDecimal.TEN,null,List.of("docs.oracle.com"));
        assertThrows(IllegalArgumentException.class,()->use.create(actor,"x","x","objective",false,1,BigDecimal.ONE,null,List.of("http://localhost")));
        assertThrows(IllegalArgumentException.class,()->use.create(actor,"x","x","objective",false,1,BigDecimal.ONE,null,List.of("http://169.254.169.254")));
    }
    @Test void onlyActiveGoalCanQueueRunAndReasonIsRequired(){
        when(port.getGoal(goal)).thenReturn(active());
        use.run(actor,goal,"manual research");
        verify(port).queueRun(actor,goal,"manual research");
        assertThrows(IllegalArgumentException.class,()->use.run(actor,goal," "));
        when(port.getGoal(goal)).thenReturn(new KnowledgeIntakePort.Goal(goal,"Java","Java","x",KnowledgeIntakePort.GoalStatus.PAUSED,false,1,BigDecimal.ONE,null,List.of("docs.oracle.com"),actor,Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,()->use.run(actor,goal,"retry"));
    }
    @Test void archivedGoalCannotBeReactivated(){
        var archived=new KnowledgeIntakePort.Goal(goal,"Java","Java","x",KnowledgeIntakePort.GoalStatus.ARCHIVED,false,1,BigDecimal.ONE,null,List.of("docs.oracle.com"),actor,Instant.EPOCH);
        when(port.getGoal(goal)).thenReturn(archived);
        assertThrows(IllegalArgumentException.class,()->use.status(actor,goal,KnowledgeIntakePort.GoalStatus.ACTIVE,"mistake"));
    }
}
