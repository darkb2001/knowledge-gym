package com.knowledgegym.presentation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.knowledgegym.content.application.ImportContentUseCase;
import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.model.QuestionQuery;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.TokenService;
import com.knowledgegym.identity.domain.port.UserRepository;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * m5 end-to-end trên Postgres thật (Testcontainers): enroll → due → review.
 *
 * <p>Assert những thứ unit test với fake **không** chứng minh được:
 * <ul>
 *   <li>`ON CONFLICT (user_id, question_id) DO NOTHING` + `RETURNING id` của native query.</li>
 *   <li>Row `study_attempts` thực sự được ghi (đọc lại từ DB, không qua port ghi).</li>
 *   <li>Cách ly giữa user: `userId` lấy từ JWT, không từ body.</li>
 * </ul>
 *
 * <p>Dùng chung context/DB nên chạy theo `@Order`: import fixture trước, các test sau dùng dữ liệu.
 */
@org.springframework.context.annotation.Import(TestEmailServiceConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QuizApiIntegrationTest {
    @Autowired com.knowledgegym.content.application.GenerateQuestionOptionsUseCase generateOptions;
    @Autowired com.knowledgegym.learning.application.SubmitQuizUseCase submitUseCase;
    @Autowired com.knowledgegym.content.domain.port.ModuleRepository moduleRepository;
    @Autowired com.knowledgegym.learning.domain.port.StudyAttemptRepository attempts;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("knowledgegym_quiz")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.content.docs-path", QuizApiIntegrationTest::fixtureDocsPath);
    }

    private static String fixtureDocsPath() {
        URL url = QuizApiIntegrationTest.class.getResource("/fixtures/docs-mini");
        assertThat(url).as("fixture docs-mini phải có trong test resources").isNotNull();
        return Path.of(url.getPath()).toString();
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ImportContentUseCase importContentUseCase;
    @Autowired QuestionRepository questionRepository;
    @Autowired UserRepository userRepository;
    @Autowired TokenService tokenService;
    @Autowired javax.sql.DataSource dataSource;
    // Port kg-core (không phải adapter) + PlatformTransactionManager của Spring: đều không thuộc
    // package bị `PresentationLayerArchTest` cấm (infrastructure.persistence / JPA).
    @Autowired com.knowledgegym.learning.domain.port.SRSCardRepository srsCardRepository;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;


    private static String userToken, otherUserToken, adminToken, moduleId, topicId;
    private static UUID userId;
    private org.springframework.jdbc.core.JdbcTemplate db(){return new org.springframework.jdbc.core.JdbcTemplate(dataSource);}
    private JsonNode response(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,int expected) throws Exception {
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postJson(String path,Object value,String token) throws Exception {return post(path).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(value));}
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder patchJson(String path,Object value,String token) throws Exception {return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(path).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(value));}
    private JsonNode generate(int count) throws Exception {return response(postJson("/quiz/generate",java.util.Map.of("moduleId",moduleId,"count",count,"strategy","RANDOM"),userToken),201);}
    private java.util.Map<String,Object> answer(JsonNode quiz,int n,boolean correct){
        String q=quiz.get("questions").get(n).get("questionId").asText();
        UUID option=db().queryForObject("SELECT id FROM question_options WHERE question_id=? AND is_correct=? LIMIT 1",UUID.class,UUID.fromString(q),correct);
        return java.util.Map.of("questionId",q,"selectedOptionId",option.toString());
    }
    @Test @Order(1) void setup() throws Exception {
        importContentUseCase.execute(fixtureDocsPath());
        userId=register("quiz@example.com");userToken=tokenService.generateAccessToken(userId,"USER");
        otherUserToken=tokenService.generateAccessToken(register("quiz-other@example.com"),"USER");
        UUID adminId=register("quiz-admin@example.com");
        User admin=userRepository.findById(adminId).orElseThrow();
        admin.setRole(com.knowledgegym.shared.domain.model.UserRole.ADMIN);
        userRepository.save(admin);
        adminToken=tokenService.generateAccessToken(adminId,"ADMIN");
        var module=moduleRepository.findBySlug("01-java-core").orElseThrow();moduleId=module.getId().toString();topicId=module.getTopicId().toString();
    }
    @Test @Order(2) void generateSubmitHistoryAndPracticeScores() throws Exception {
        var quiz=generate(3);String id=quiz.get("id").asText();assertThat(quiz.get("total").asInt()).isEqualTo(3);
        for(var q:quiz.get("questions"))for(var o:q.get("options"))assertThat(o.has("correct")||o.has("isCorrect")).isFalse();
        var result=response(postJson("/quiz/"+id+"/submit",java.util.Map.of("answers",List.of(answer(quiz,0,true),answer(quiz,1,false))),userToken),200);
        assertThat(result.get("score").asInt()).isEqualTo(33);assertThat(result.get("breakdown").size()).isEqualTo(3);
        assertThat(db().queryForObject("SELECT count(*) FROM study_attempts WHERE user_id=? AND source='PRACTICE'",Integer.class,userId)).isEqualTo(3);
        assertThat(db().queryForObject("SELECT count(*) FROM study_attempts WHERE user_id=? AND source='PRACTICE' AND score IS NOT NULL",Integer.class,userId)).isEqualTo(3);
        response(postJson("/quiz/"+id+"/submit",java.util.Map.of("answers",List.of()),userToken),409);
        assertThat(db().queryForObject("SELECT count(*) FROM quiz_answers WHERE session_id=?",Integer.class,UUID.fromString(id))).isEqualTo(3);
        var history=response(get("/quiz/history").header("Authorization","Bearer "+userToken),200);assertThat(history.get("items").size()).isGreaterThan(0);
        response(get("/quiz/"+id).header("Authorization","Bearer "+otherUserToken),404);
        response(postJson("/quiz/"+id+"/submit",java.util.Map.of("answers",List.of()),otherUserToken),404);
    }
    @Test @Order(3) void invalidCountsNullAnswersAndCrossQuestionOptionsReturn400() throws Exception {
        for(int count:new int[]{0,51})response(postJson("/quiz/generate",java.util.Map.of("moduleId",moduleId,"count",count,"strategy","RANDOM"),userToken),400);
        var quiz=generate(2);String path="/quiz/"+quiz.get("id").asText()+"/submit";
        response(postJson(path,java.util.Map.of("answers",java.util.Arrays.asList((Object)null)),userToken),400);
        var a=answer(quiz,0,true);var b=answer(quiz,1,true);
        response(postJson(path,java.util.Map.of("answers",List.of(java.util.Map.of("questionId",a.get("questionId"),"selectedOptionId",b.get("selectedOptionId")))),userToken),400);
        assertThat(db().queryForObject("SELECT count(*) FROM quiz_answers WHERE session_id=?",Integer.class,UUID.fromString(quiz.get("id").asText()))).isZero();
    }
    @Test @Order(4) void rollbackWhenPracticeInsertFails() throws Exception {
        var quiz=generate(1);UUID id=UUID.fromString(quiz.get("id").asText());var a=answer(quiz,0,true);
        db().execute("CREATE FUNCTION reject_practice() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.source='PRACTICE' THEN RAISE EXCEPTION 'test rollback'; END IF; RETURN NEW; END $$");
        db().execute("CREATE TRIGGER reject_practice BEFORE INSERT ON study_attempts FOR EACH ROW EXECUTE FUNCTION reject_practice()");
        try {
            assertThatThrownBy(()->submitUseCase.execute(userId,id,new com.knowledgegym.learning.application.SubmitQuizUseCase.SubmitCommand(List.of(new com.knowledgegym.learning.application.SubmitQuizUseCase.SubmittedAnswer(UUID.fromString(a.get("questionId").toString()),UUID.fromString(a.get("selectedOptionId").toString()),null))))).isInstanceOf(RuntimeException.class);
            assertThat(db().queryForObject("SELECT count(*) FROM quiz_answers WHERE session_id=?",Integer.class,id)).isZero();
            assertThat(db().queryForObject("SELECT finished_at IS NULL FROM quiz_sessions WHERE id=?",Boolean.class,id)).isTrue();
        }finally{db().execute("DROP TRIGGER reject_practice ON study_attempts");db().execute("DROP FUNCTION reject_practice()");}
    }
    @Test @Order(5) void backfillIsStableAndPublicQuestionOptionsHideCorrectFlag() throws Exception {
        var quiz=generate(1);String qid=quiz.get("questions").get(0).get("questionId").asText();
        var before=db().queryForList("SELECT id FROM question_options WHERE question_id=? ORDER BY display_order",UUID.class,UUID.fromString(qid));
        response(post("/admin/content/questions/generate-options").header("Authorization","Bearer "+userToken),403);
        var result=response(post("/admin/content/questions/generate-options").header("Authorization","Bearer "+adminToken),200);
        assertThat(result.get("eligible").asInt()).isGreaterThanOrEqualTo((int)Math.ceil(result.get("questions").asInt()*0.9));
        var coverage = db().queryForObject("SELECT count(DISTINCT q.id) FROM questions q JOIN question_options o ON o.question_id=q.id WHERE q.module_id=?",Integer.class,UUID.fromString(moduleId));
        assertThat(coverage).isGreaterThanOrEqualTo((int)Math.ceil(moduleRepository.countQuestions(UUID.fromString(moduleId))*0.9));
        assertThat(db().queryForList("SELECT id FROM question_options WHERE question_id=? ORDER BY display_order",UUID.class,UUID.fromString(qid))).containsExactlyElementsOf(before);
        var detail=response(get("/questions/"+qid).header("Authorization","Bearer "+userToken),200);
        assertThat(detail.get("options").size()).isGreaterThanOrEqualTo(2);assertThat(detail.get("options").get(0).has("isCorrect")).isFalse();
    }
    @Test @Order(6) void mockInterviewSubmitsEverythingAtOnceAndScopesOwnership() throws Exception {
        var start=response(postJson("/mock-interview/start",java.util.Map.of("topicId",topicId,"questionCount",3,"mode","TEXT"),userToken),201);
        String id=start.get("session").get("id").asText();String qid=start.get("questions").get(0).get("questionId").asText();
        String blankQid=start.get("questions").get(2).get("questionId").asText();
        // Phiên của người khác: không submit được, không đọc được trang kết quả.
        response(postJson("/mock-interview/"+id+"/submit",java.util.Map.of("answers",List.of(java.util.Map.of("questionId",qid,"answer","thread lock"))),otherUserToken),404);
        response(get("/mock-interview/"+id+"/result").header("Authorization","Bearer "+otherUserToken),404);
        String sample=questionRepository.findById(UUID.fromString(qid)).orElseThrow().getAnswerHtml();
        // Một nút "kết thúc phỏng vấn" = submit toàn cục: mọi câu đều có đáp án mẫu, kể cả câu bỏ trống; không chấm điểm.
        var submitted=response(postJson("/mock-interview/"+id+"/submit",java.util.Map.of("answers",List.of(
                java.util.Map.of("questionId",qid,"answer","thread lock và lock striping"),
                java.util.Map.of("questionId",blankQid,"answer","   "))),userToken),200);
        assertThat(submitted.get("items")).hasSize(3);
        assertThat(submitted.get("answeredCount").asInt()).isEqualTo(1);
        assertThat(submitted.get("session").get("status").asText()).isEqualTo("FINISHED");
        var answered=submitted.get("items").get(0);
        assertThat(answered.get("questionId").asText()).isEqualTo(qid);
        assertThat(answered.get("userAnswer").asText()).isEqualTo("thread lock và lock striping");
        assertThat(answered.get("answerHtml").asText()).isEqualTo(sample);
        var blankAnswer=submitted.get("items").get(2).get("userAnswer");
        assertThat(blankAnswer==null||blankAnswer.isNull()).isTrue();
        assertThat(submitted.get("items").get(2).hasNonNull("answerHtml")).isTrue();
        assertThat(submitted.has("overallScore")).isFalse();
        assertThat(answered.has("keywordScore")).isFalse();assertThat(answered.has("feedback")).isFalse();
        assertThat(db().queryForObject("SELECT count(*) FROM interview_answers WHERE session_id=? AND question_id=?",Integer.class,UUID.fromString(id),UUID.fromString(qid))).isEqualTo(1);
        assertThat(db().queryForObject("SELECT answer_html FROM interview_answers WHERE session_id=? AND question_id=?",String.class,UUID.fromString(id),UUID.fromString(qid))).isEqualTo(sample);
        // Bỏ trống không tạo thêm dòng: 3 dòng là placeholder của phiên lúc start, chỉ 1 dòng có câu trả lời.
        assertThat(db().queryForObject("SELECT count(*) FROM interview_answers WHERE session_id=?",Integer.class,UUID.fromString(id))).isEqualTo(3);
        assertThat(db().queryForObject("SELECT count(*) FROM interview_answers WHERE session_id=? AND user_answer IS NOT NULL",Integer.class,UUID.fromString(id))).isEqualTo(1);
        assertThat(db().queryForObject("SELECT count(*) FROM interview_answers WHERE session_id=? AND question_id=? AND user_answer IS NULL",Integer.class,UUID.fromString(id),UUID.fromString(blankQid))).isEqualTo(1);
        // Đọc lại trang kết quả được; phiên đã đóng nên submit/finish lần nữa -> 409.
        var reread=response(get("/mock-interview/"+id+"/result").header("Authorization","Bearer "+userToken),200);
        assertThat(reread.get("items")).hasSize(3);
        response(postJson("/mock-interview/"+id+"/submit",java.util.Map.of("answers",List.of()),userToken),409);
        response(post("/mock-interview/"+id+"/finish").header("Authorization","Bearer "+userToken),409);
        // Endpoint lưu từng câu đã bị gỡ: FE chỉ còn một nút submit toàn cục.
        response(postJson("/mock-interview/"+id+"/answer",java.util.Map.of("questionId",qid,"userAnswer","edited"),userToken),404);
        response(postJson("/mock-interview/start",java.util.Map.of("topicId",topicId,"questionCount",1,"mode","AUDIO"),userToken),400);
        for(int count:new int[]{0,21})response(postJson("/mock-interview/start",java.util.Map.of("topicId",topicId,"questionCount",count,"mode","TEXT"),userToken),400);
        var history=response(get("/mock-interview/history").header("Authorization","Bearer "+userToken),200);
        var persisted=history.get("items").get(0);
        assertThat(persisted.get("status").asText()).isEqualTo("FINISHED");
        assertThat(persisted.get("questionIds")).hasSize(3);
        assertThat(persisted.has("overallScore")).isFalse();
        // V017: mọi dòng của phiên (kể cả câu bỏ trống) theo đúng thứ tự đã giao, không phải theo UUID của placeholder.
        assertThat(db().queryForList("SELECT question_id::text FROM interview_answers WHERE session_id=? ORDER BY display_order",String.class,UUID.fromString(id)))
                .isEqualTo(start.get("questions").findValuesAsString("questionId"));
        assertThat(db().queryForList("SELECT question_id::text FROM interview_answers WHERE session_id=? AND user_answer IS NOT NULL ORDER BY display_order",String.class,UUID.fromString(id)))
                .containsExactly(qid);
    }
    @Test @Order(10) void adminCreatedQuestionGetsQuizOptionsImmediately() throws Exception {
        var created=response(postJson("/admin/content/questions",java.util.Map.of("moduleId",moduleId,
                "title","Admin tạo câu mới?","answerHtml","<p>Câu trả lời admin vừa nhập.</p>","difficulty","MID","tags",List.of("admin")),adminToken),201);
        String qid=created.get("id").asText();
        assertThat(db().queryForObject("SELECT count(*) FROM question_options WHERE question_id=?",
                Integer.class,UUID.fromString(qid))).isGreaterThanOrEqualTo(2);
        assertThat(db().queryForObject("SELECT count(*) FROM question_options WHERE question_id=? AND is_correct",
                Integer.class,UUID.fromString(qid))).isEqualTo(1);
        // Response admin phải mang options vừa sinh — trước đây luôn trả `[]` dù DB đã có.
        assertThat(created.get("options").size()).isGreaterThanOrEqualTo(2);
        assertThat(created.get("options").findValuesAsString("id")).containsAll(
                db().queryForList("SELECT id::text FROM question_options WHERE question_id=?",String.class,UUID.fromString(qid)));
        assertThat(created.get("options").findValues("isCorrect").stream().filter(JsonNode::asBoolean).count()).isEqualTo(1);
    }
    @Test @Order(11) void editingQuestionKeepsChosenOptionIdsOfPastQuizzes() throws Exception {
        var quiz=response(postJson("/quiz/generate",java.util.Map.of("moduleId",moduleId,"count",1,"strategy","RANDOM"),userToken),201);
        String qid=quiz.get("questions").get(0).get("questionId").asText();
        String optionId=db().queryForObject("SELECT id::text FROM question_options WHERE question_id=? AND is_correct",String.class,UUID.fromString(qid));
        String quizId=quiz.get("id").asText();
        response(postJson("/quiz/"+quizId+"/submit",java.util.Map.of("answers",
                List.of(java.util.Map.of("questionId",qid,"selectedOptionId",optionId))),userToken),200);
        // Sửa câu rồi generate lại option: `selected_option_id` cũ thành NULL (FK ON DELETE SET NULL)
        // nhưng snapshot `answer_text` phải giữ nội dung user đã chọn.
        response(patchJson("/admin/content/questions/"+qid,java.util.Map.of("title","Tiêu đề đổi"),adminToken),200);
        assertThat(db().queryForObject("SELECT answer_text FROM quiz_answers WHERE session_id=? AND question_id=?",
                String.class,UUID.fromString(quizId),UUID.fromString(qid))).isNotBlank();
        assertThat(db().queryForObject("SELECT count(*) FROM question_options WHERE question_id=? AND is_correct",
                Integer.class,UUID.fromString(qid))).isEqualTo(1);
    }
    @Test @Order(7) void weaknessUsesWrongAttemptsAndSpacedOnlyDueCards() throws Exception {
        UUID wrong=db().queryForObject("SELECT question_id FROM study_attempts WHERE user_id=? AND is_correct=false LIMIT 1",UUID.class,userId);
        assertThat(attempts.findWrongQuestionIdsByUser(userId)).contains(wrong);
        var weak=response(postJson("/quiz/generate",java.util.Map.of("moduleId",moduleId,"count",1,"strategy","WEAKNESS"),userToken),201);
        assertThat(attempts.findWrongQuestionIdsByUser(userId)).contains(UUID.fromString(weak.get("questions").get(0).get("questionId").asText()));
        db().update("INSERT INTO srs_cards(user_id,question_id,next_review) VALUES (?,?,current_date)",userId,wrong);
        var spaced=response(postJson("/quiz/generate",java.util.Map.of("moduleId",moduleId,"count",50,"strategy","SPACED"),userToken),201);
        assertThat(spaced.get("total").asInt()).isEqualTo(1);assertThat(spaced.get("questions").get(0).get("questionId").asText()).isEqualTo(wrong.toString());
    }
    @Test @Order(8) void concurrentSubmitIsProtectedWhenRedisDebounceFailsOpen() throws Exception {
        var quiz=generate(2);UUID id=UUID.fromString(quiz.get("id").asText());
        REDIS.stop();
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        var barrier=new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.Callable<Boolean> task=()->{barrier.await();try{submitUseCase.execute(userId,id,new com.knowledgegym.learning.application.SubmitQuizUseCase.SubmitCommand(List.of()));return true;}catch(com.knowledgegym.shared.application.ConflictException e){return false;}};
        try{var one=executor.submit(task);var two=executor.submit(task);assertThat(List.of(one.get(30,java.util.concurrent.TimeUnit.SECONDS),two.get(30,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(db().queryForObject("SELECT count(*) FROM quiz_answers WHERE session_id=?",Integer.class,id)).isEqualTo(2);
            assertThat(db().queryForObject("SELECT count(*) FROM study_attempts WHERE user_id=? AND attempted_at=(SELECT finished_at FROM quiz_sessions WHERE id=?)",Integer.class,userId,id)).isEqualTo(2);
        }finally{executor.shutdownNow();}
    }
    @Test @Order(9) void deletingReferencedQuestionIsRejectedAndPreservesHistory() throws Exception {
        var quiz=generate(1);String id=quiz.get("id").asText();String qid=quiz.get("questions").get(0).get("questionId").asText();
        response(postJson("/quiz/"+id+"/submit",java.util.Map.of("answers",List.of(answer(quiz,0,true))),userToken),200);
        var start=response(postJson("/mock-interview/start",java.util.Map.of("topicId",topicId,"questionCount",20,"mode","TEXT"),userToken),201);
        String interviewId=start.get("session").get("id").asText();
        response(postJson("/mock-interview/"+interviewId+"/submit",java.util.Map.of("answers",List.of(java.util.Map.of("questionId",qid,"answer","java"))),userToken),200);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/admin/content/questions/"+qid).header("Authorization","Bearer "+adminToken)).andExpect(status().isConflict());
        assertThat(db().queryForObject("SELECT count(*) FROM quiz_sessions WHERE id=?",Integer.class,UUID.fromString(id))).isEqualTo(1);
        assertThat(db().queryForObject("SELECT count(*) FROM interview_sessions WHERE id=?",Integer.class,UUID.fromString(interviewId))).isEqualTo(1);
        assertThat(db().queryForObject("SELECT count(*) FROM interview_answers WHERE question_id=?",Integer.class,UUID.fromString(qid))).isGreaterThanOrEqualTo(1);
    }
    @Test @Order(12) void adminClaimCannotElevateAUserWhoseDatabaseRoleIsUser() throws Exception {
        UUID ordinaryUser=userRepository.findByEmail("quiz-other@example.com").orElseThrow().getId();
        String staleOrForgedAdminClaim=tokenService.generateAccessToken(ordinaryUser,"ADMIN");
        response(get("/admin/content/questions").header("Authorization","Bearer "+staleOrForgedAdminClaim),403);
    }
    private UUID register(String email) throws Exception {
        String code = RegistrationTestSupport.code(mockMvc, email);
        response(postJson("/auth/register",java.util.Map.of("email",email,"password","superSecret123","confirmPassword","superSecret123","displayName","Quiz Tester","verificationCode",code),"")
                .header("X-Forwarded-For", RegistrationTestSupport.nextIp()),201);
        return userRepository.findByEmail(email).orElseThrow().getId();
    }
}
