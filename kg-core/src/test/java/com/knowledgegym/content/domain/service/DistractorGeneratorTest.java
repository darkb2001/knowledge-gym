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
 @Test void labelPrefixFromDocsIsStripped(){
  UUID module=UUID.randomUUID();
  var target=q(module,"<p><span class=\"ans-label\">What</span> Lambda chạy code mà không cần quản lý server.</p>");
  target.setTitle("AWS Lambda là gì?");
  var siblings=List.of(q(module,"Đây là nhiễu một."),q(module,"Đây là nhiễu hai."),q(module,"Đây là nhiễu ba."));
  var options=DistractorGenerator.generate(target,siblings,List.of());
  assertThat(options.stream().map(QuestionOption::getContent)).noneMatch(c -> c.startsWith("What"));
  assertThat(options.stream().filter(QuestionOption::isCorrect).map(QuestionOption::getContent))
    .containsExactly("Lambda chạy code mà không cần quản lý server.");
 }
 @Test void optionsAreTrimmedToTheLengthCap(){
  UUID module=UUID.randomUUID();
  var target=q(module,"<p>Câu trả lời đúng ngắn gọn.</p>");
  target.setTitle("Câu hỏi dài?");
  String longSentence="S" + "x".repeat(300) + " hết câu.";
  var siblings=List.of(q(module,longSentence),q(module,longSentence.replace("Sx","Sy")),q(module,"Ngắn."));
  var options=DistractorGenerator.generate(target,siblings,List.of());
  assertThat(options).isNotEmpty();
  assertThat(options.stream().map(QuestionOption::getContent)).allMatch(c -> c.length() <= 161);
 }
 @Test void distractorPrefersSiblingThatSharesTopicWithTheQuestion(){
  UUID module=UUID.randomUUID();
  var target=q(module,"<p>Eager loading nạp ngay khi truy vấn gốc.</p>");
  target.setTitle("Phân biệt eager loading và lazy loading trong JPA");
  var siblings=List.of(
    q(module,"Sourdough bread lên men chậm ở nhiệt độ phòng."),
    q(module,"Lazy loading chỉ nạp dữ liệu khi bạn thật sự truy cập."),
    q(module,"Pizza Napoli cần lò nướng 450 độ."),
    q(module,"Eager loading nạp hết dữ liệu liên quan ngay từ truy vấn đầu."),
    q(module,"Cà phê rang ở nhiệt độ thấp sẽ chua hơn."),
    q(module,"Cả lazy loading và eager loading đều dùng cho quan hệ thực thể."));
  var options=DistractorGenerator.generate(target,siblings,List.of());
  assertThat(options.stream().map(QuestionOption::getContent))
    .contains("Lazy loading chỉ nạp dữ liệu khi bạn thật sự truy cập.")
    .doesNotContain("Sourdough bread lên men chậm ở nhiệt độ phòng.")
    .doesNotContain("Pizza Napoli cần lò nướng 450 độ.")
    .doesNotContain("Cà phê rang ở nhiệt độ thấp sẽ chua hơn.");
 }
 @Test void singletonOrBlankAnswersCannotProduceMcq(){var q=q(UUID.randomUUID(),"One answer.");assertThat(DistractorGenerator.generate(q,List.of(q),List.of())).isEmpty();q.setAnswerHtml("<p></p>");assertThat(DistractorGenerator.generate(q,List.of(),List.of())).isEmpty();}
}
