package com.knowledgegym.content.domain.service;
import com.knowledgegym.content.domain.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class DistractorGeneratorTest {
 private Question q(UUID module,String html){var q=new Question();q.setId(UUID.randomUUID());q.setModuleId(module);q.setAnswerHtml(html);return q;}
 @Test void deterministicUniqueOptionsPreferModuleOverTopic(){
  UUID module=UUID.randomUUID();var target=q(module,"<p>Correct &amp; safe.</p>");
  var siblings=List.of(q(module,"Sibling one."),q(module,"Sibling two."),q(module,"Sibling three."),q(module,"Correct &amp; safe."));
  var fallback=List.of(q(UUID.randomUUID(),"Topic fallback."));
  var first=DistractorGenerator.generate(target,siblings,fallback);
  var second=DistractorGenerator.generate(target,siblings,fallback);
  assertThat(first.stream().map(QuestionOption::getContent)).containsExactlyElementsOf(second.stream().map(QuestionOption::getContent).toList()).doesNotHaveDuplicates().doesNotContain("Topic fallback.");
  assertThat(first.stream().filter(QuestionOption::isCorrect)).hasSize(1);
 }
 @Test void singletonOrBlankAnswersCannotProduceMcq(){var q=q(UUID.randomUUID(),"One answer.");assertThat(DistractorGenerator.generate(q,List.of(q),List.of())).isEmpty();q.setAnswerHtml("<p></p>");assertThat(DistractorGenerator.generate(q,List.of(),List.of())).isEmpty();}
}
