package com.knowledgegym.presentation;

import com.knowledgegym.english.application.EnglishCatalog;
import com.knowledgegym.english.application.EnglishPracticeUseCase;
import com.knowledgegym.english.domain.model.EnglishAttempt;
import com.knowledgegym.presentation.advice.GlobalExceptionHandler;
import com.knowledgegym.presentation.rest.english.EnglishPracticeController;
import com.knowledgegym.shared.application.ConflictException;
import com.knowledgegym.shared.application.NotFoundException;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** MVC serialization + real security chain, mocked application port; not live JWT/DB acceptance. */
class EnglishPracticeHttpTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private EnglishPracticeUseCase service;
    private final UUID user = UUID.randomUUID(), id = UUID.randomUUID();
    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
    private EnglishAttempt attempt(String status) {
        return new EnglishAttempt(id, user, "listening-announcement-v1", status, status.equals("DRAFT") ? 0 : 1,
            Map.of("q1", 1, "q2", 2, "q3", 1), "", 40, Instant.now(), Instant.now());
    }
    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext()); context.register(Config.class); context.refresh();
        service = context.getBean(EnglishPracticeUseCase.class);
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }
    @Test void everyRouteRequiresAuthentication() throws Exception {
        for (String path : List.of("/english/exercises", "/english/attempts", "/english/attempts/" + id))
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post("/english/attempts").contentType(MediaType.APPLICATION_JSON).content("{\"exerciseId\":\"writing-email-v1\"}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void catalogDoesNotLeakAnswerKeysOrTranscripts() throws Exception {
        mvc.perform(get("/english/exercises").with(authentication(auth())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(39))
            .andExpect(jsonPath("$[0].items[0].correctIndex").doesNotExist())
            .andExpect(jsonPath("$[0].items[0].explanation").doesNotExist())
            .andExpect(jsonPath("$[0].transcript").doesNotExist())
            .andExpect(jsonPath("$[*].referenceResponse").doesNotExist())
            .andExpect(jsonPath("$[*].text").doesNotExist())
            .andExpect(jsonPath("$[0].audioPath").value("/english/audio/announcement-v1.mp3"));
    }
    @Test void draftHidesFeedbackAndStartUsesPrincipal() throws Exception {
        when(service.start(user, "listening-announcement-v1")).thenReturn(attempt("DRAFT"));
        mvc.perform(post("/english/attempts").with(authentication(auth())).contentType(MediaType.APPLICATION_JSON)
            .content("{\"exerciseId\":\"listening-announcement-v1\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.feedback").isEmpty())
            .andExpect(jsonPath("$.userId").doesNotExist());
        verify(service).start(user, "listening-announcement-v1");
    }
    @Test void submittedObjectiveGetsRawCountAndExplanationNotCertificateGrade() throws Exception {
        when(service.get(user, id)).thenReturn(attempt("SUBMITTED"));
        mvc.perform(get("/english/attempts/" + id).with(authentication(auth())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.feedback.correct").value(2))
            .andExpect(jsonPath("$.feedback.total").value(3)).andExpect(jsonPath("$.feedback.items[0].correctIndex").value(1))
            .andExpect(jsonPath("$.feedback.transcript").isNotEmpty()).andExpect(jsonPath("$.vstepScore").doesNotExist());
    }
    @Test void crossOwnerNotFoundAndStaleWritesConflict() throws Exception {
        when(service.get(user, id)).thenThrow(new NotFoundException("English attempt not found"));
        mvc.perform(get("/english/attempts/" + id).with(authentication(auth()))).andExpect(status().isNotFound());
        when(service.save(user, id, 0, Map.of(), "My text", 10, false)).thenThrow(new ConflictException("This attempt changed"));
        mvc.perform(put("/english/attempts/" + id).with(authentication(auth())).contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":0,\"answers\":{},\"response\":\"My text\",\"elapsedSeconds\":10,\"submit\":false}"))
            .andExpect(status().isConflict());
        verify(service).save(user, id, 0, Map.of(), "My text", 10, false);
    }
    @Test void subjectiveWorkGetsNoNumericGrade() throws Exception {
        var subjective = new EnglishAttempt(id, user, "writing-email-v1", "SUBMITTED", 1, Map.of(),
            "Dear Alex", 60, Instant.now(), Instant.now());
        when(service.get(user, id)).thenReturn(subjective);
        mvc.perform(get("/english/attempts/" + id).with(authentication(auth())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.feedback.correct").isEmpty())
            .andExpect(jsonPath("$.feedback.total").value(0))
            .andExpect(jsonPath("$.feedback.referenceResponse.text").isNotEmpty())
            .andExpect(jsonPath("$.feedback.referenceResponse.notes[0].vi").isNotEmpty());
    }
    @Test void allSubjectiveModelsAreHiddenInDraftAndVisibleOnlyInSubmittedOwnedFeedback() throws Exception {
        for (var exercise : new EnglishCatalog().list()) {
            if (!exercise.items().isEmpty()) continue;
            if (exercise.skill() != com.knowledgegym.english.domain.model.EnglishExercise.Skill.WRITING
                    && exercise.skill() != com.knowledgegym.english.domain.model.EnglishExercise.Skill.SPEAKING) continue;
            for (String state : List.of("DRAFT", "SUBMITTED")) {
                var attempt = new EnglishAttempt(id, user, exercise.id(), state, state.equals("DRAFT") ? 0 : 1,
                    Map.of(), "Original learner response", 120, Instant.now(), Instant.now());
                when(service.get(user, id)).thenReturn(attempt);
                var result = mvc.perform(get("/english/attempts/" + id).with(authentication(auth())))
                    .andExpect(status().isOk());
                if (state.equals("DRAFT")) result.andExpect(jsonPath("$.feedback").isEmpty());
                else result.andExpect(jsonPath("$.feedback.referenceResponse.text").isNotEmpty())
                    .andExpect(jsonPath("$.feedback.correct").isEmpty())
                    .andExpect(jsonPath("$.vstepScore").doesNotExist());
            }
        }
    }
    @Test void fullSectionsExposeGroupingButNoKeysTranscriptsOrReferenceTextInCatalog() throws Exception {
        var body = mvc.perform(get("/english/exercises").with(authentication(auth())))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains("reading-complete-practice-v1", "listening-complete-practice-v1", "itemIds", "COMPLETE_SKILL")
            .doesNotContain("\"correctIndex\"", "\"explanation\"", "\"transcript\"", "\"referenceResponse\"");
    }
    @Test void anotherPrincipalCannotRetrieveTheOwnersSubmittedReference() throws Exception {
        var other = UUID.randomUUID();
        var otherAuth = new UsernamePasswordAuthenticationToken(other, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(service.get(other, id)).thenThrow(new NotFoundException("English attempt not found"));
        var body = mvc.perform(get("/english/attempts/" + id).with(authentication(otherAuth)))
            .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("referenceResponse", "Dear Alex");
        verify(service).get(other, id);
    }
    @Configuration @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config {
        @Bean EnglishCatalog catalog() { return new EnglishCatalog(); }
        @Bean EnglishPracticeUseCase service() { return mock(EnglishPracticeUseCase.class); }
        @Bean EnglishPracticeController controller(EnglishCatalog catalog, EnglishPracticeUseCase service) {
            return new EnglishPracticeController(catalog, service);
        }
        @Bean GlobalExceptionHandler advice() { return new GlobalExceptionHandler(JsonMapper.builder().build()); }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.setStatus(401))).build();
        }
    }
}
