package com.knowledgegym.learning.application;
import com.knowledgegym.content.domain.model.*;
import com.knowledgegym.learning.application.strategy.*;
import com.knowledgegym.learning.domain.model.*;
import com.knowledgegym.shared.domain.model.Difficulty;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class QuizStrategyTest {
 @Test void strategiesUseWeaknessDifficultyAndOnlyDueCards(){
  var q=new LearningTestSupport.InMemoryQuestionRepository();var m=new LearningTestSupport.InMemoryModuleRepository();var a=new LearningTestSupport.InMemoryStudyAttemptRepository();var cards=new LearningTestSupport.InMemorySRSCardRepository();
  UUID user=UUID.randomUUID();var module=new ModuleRef("quiz-module", "Quiz", 1);module.setId(UUID.randomUUID());m.seed(module);
  var junior=LearningTestSupport.question(module.getId(),"Junior",1);junior.setDifficulty(Difficulty.JUNIOR);
  var mid=LearningTestSupport.question(module.getId(),"Mid",2);var senior=LearningTestSupport.question(module.getId(),"Senior",3);senior.setDifficulty(Difficulty.SENIOR);
  for(var question:List.of(junior,mid,senior))q.seed(question);
  var extras=new ArrayList<UUID>();
  for(int n=0;n<3;n++){var extra=LearningTestSupport.question(module.getId(),"Extra "+n,n+4);extra.setDifficulty(Difficulty.JUNIOR);q.seed(extra);extras.add(extra.getId());}
  Clock clock=Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"),ZoneOffset.UTC);
  a.save(StudyAttempt.record(user,junior.getId(),AttemptSource.FLASHCARD,false,null,clock.instant()));
  cards.save(SRSCard.newCard(user,senior.getId(),null,null,LocalDate.now(clock)));
  cards.save(SRSCard.newCard(user,mid.getId(),null,null,LocalDate.now(clock).plusDays(1)));
  var pool=new QuizCandidatePool(q,m);
  assertThat(new RandomQuizStrategy(pool).rank(user,module.getId(),null,new Random(1))).hasSize(6).contains(junior.getId(),mid.getId(),senior.getId());
  assertThat(new WeaknessQuizStrategy(pool,a).rank(user,module.getId(),null,new Random(1))).startsWith(junior.getId());
  assertThat(new InterviewQuizStrategy(pool).rank(user,module.getId(),null,new Random(1))).doesNotHaveDuplicates();
  var randomRank=new RandomQuizStrategy(pool).rank(user,module.getId(),null,new Random(1));
  var weaknessRank=new WeaknessQuizStrategy(pool,a).rank(user,module.getId(),null,new Random(1));
  var interviewRank=new InterviewQuizStrategy(pool).rank(user,module.getId(),null,new Random(1));
  var spacedRank=new SpacedQuizStrategy(pool,cards,clock).rank(user,module.getId(),null,new Random(1));
  assertThat(interviewRank.subList(0,2)).containsExactlyInAnyOrder(mid.getId(),senior.getId());
  assertThat(List.of(randomRank.getFirst(),weaknessRank.getFirst(),interviewRank.getFirst(),spacedRank.getFirst())).doesNotHaveDuplicates();
  assertThat(new RandomQuizStrategy(pool).rank(user,module.getId(),Difficulty.JUNIOR,new Random(1))).doesNotContain(mid.getId(),senior.getId());
  assertThat(new SpacedQuizStrategy(pool,cards,clock).rank(user,module.getId(),null,new Random(1))).containsExactly(senior.getId());
 }
}
