package com.knowledgegym.presentation;

import com.knowledgegym.identity.application.ManageUsersUseCase;
import com.knowledgegym.identity.domain.port.AdminUserPort;
import com.knowledgegym.presentation.rest.admin.AdminUsersController;
import com.knowledgegym.shared.domain.model.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** Real MVC + method-security chain, isolated from Docker, Redis and background agents. */
class AdminUsersHttpTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private ManageUsersUseCase service;
    private final UUID actor = UUID.randomUUID(), target = UUID.randomUUID();
    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext()); context.register(Config.class); context.refresh();
        service = context.getBean(ManageUsersUseCase.class);
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }
    @Test void anonymousGets401() throws Exception {
        mvc.perform(get("/admin/users")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void userAndPremiumCannotReadOrMutate() throws Exception {
        for (String role : List.of("USER", "PREMIUM")) {
            mvc.perform(get("/admin/users").with(authentication(auth(role)))).andExpect(status().isForbidden());
            mvc.perform(patch("/admin/users/" + target + "/role").with(authentication(auth(role)))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\",\"reason\":\"test\"}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void adminDirectoryHasPaginationAndNoSecrets() throws Exception {
        when(service.list(null, null, null, 1, 20)).thenReturn(
                new AdminUserPort.UserPage(List.of(view()), 1, 20, 1, 1));
        mvc.perform(get("/admin/users").with(authentication(auth("ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(target.toString()))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.items[0].oauthId").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test void adminMutationUsesAuthenticatedActorAndRequiresReason() throws Exception {
        when(service.changeRole(actor, target, UserRole.PREMIUM, "Subscription")).thenReturn(view());
        mvc.perform(patch("/admin/users/" + target + "/role").with(authentication(auth("ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"PREMIUM\",\"reason\":\"Subscription\"}"))
                .andExpect(status().isOk());
        verify(service).changeRole(actor, target, UserRole.PREMIUM, "Subscription");
        mvc.perform(patch("/admin/users/" + target + "/status").with(authentication(auth("ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"blocked\":true,\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).setBlocked(any(), any(), anyBoolean(), any());
    }
    @Test void sessionRevocationReturns204() throws Exception {
        mvc.perform(post("/admin/users/" + target + "/revoke-sessions").with(authentication(auth("ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Suspected theft\"}"))
                .andExpect(status().isNoContent());
        verify(service).revokeSessions(actor, target, "Suspected theft");
    }
    @Test void moderationAndLearningRoutesEnforceAdmin() throws Exception {
        for (String path : List.of("/admin/blog/comments", "/admin/users/" + target + "/learning?kind=QUIZ")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(authentication(auth("USER")))).andExpect(status().isForbidden());
        }
        mvc.perform(delete("/admin/blog/comments/" + target).with(authentication(auth("PREMIUM")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Spam\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/users/" + target + "/learning/srs/" + actor + "/reset").with(authentication(auth("USER")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Reset\"}"))
                .andExpect(status().isForbidden());
    }
    @Test void adminModerationAndResetRequireReasonAndForwardActor() throws Exception {
        var moderation=context.getBean(com.knowledgegym.blog.application.ModerateBlogUseCase.class);
        mvc.perform(delete("/admin/blog/posts/" + target).with(authentication(auth("ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Outdated\"}"))
                .andExpect(status().isOk());
        verify(moderation).post(actor,target,com.knowledgegym.blog.domain.port.AdminModerationPort.PostAction.DELETE,"Outdated");
        mvc.perform(post("/admin/users/" + target + "/learning/srs/" + actor + "/reset").with(authentication(auth("ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(context.getBean(com.knowledgegym.learning.application.AdminLearningUseCase.class));
    }
    @Test void learningDraftRoutesRequireAdminAndRollbackForwardsActor() throws Exception {
        var drafts=context.getBean(com.knowledgegym.blog.application.GenerateLearningDraftUseCase.class);
        for(String role:List.of("USER","PREMIUM")) {
            mvc.perform(get("/admin/knowledge/drafts").with(authentication(auth(role)))).andExpect(status().isForbidden());
            mvc.perform(post("/admin/knowledge/drafts/"+target+"/rollback").with(authentication(auth(role)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Wrong answer\"}")).andExpect(status().isForbidden());
        }
        mvc.perform(get("/admin/knowledge/drafts")).andExpect(status().isUnauthorized());
        verifyNoInteractions(drafts);
        when(drafts.rollback(actor,target,"Wrong answer")).thenReturn(actor);
        mvc.perform(post("/admin/knowledge/drafts/"+target+"/rollback").with(authentication(auth("ADMIN")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Wrong answer\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.questionId").value(actor.toString()));
        verify(drafts).rollback(actor,target,"Wrong answer");
        mvc.perform(post("/admin/knowledge/drafts/"+target+"/rollback").with(authentication(auth("ADMIN")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());
    }

    @Test void questionLifecycleReadAndMutationRequireAdmin() throws Exception {
        for (String role : List.of("USER", "PREMIUM")) {
            mvc.perform(get("/admin/content/questions").with(authentication(auth(role)))).andExpect(status().isForbidden());
            mvc.perform(get("/admin/content/questions/" + target).with(authentication(auth(role)))).andExpect(status().isForbidden());
            mvc.perform(patch("/admin/content/questions/" + target + "/status").with(authentication(auth(role)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIDDEN\",\"reason\":\"Review\"}")).andExpect(status().isForbidden());
        }
        mvc.perform(get("/admin/content/questions")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/admin/content/questions/" + target + "/status")
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIDDEN\",\"reason\":\"Review\"}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(context.getBean(com.knowledgegym.content.application.ManageQuestionsUseCase.class));
        verifyNoInteractions(context.getBean(com.knowledgegym.content.application.QueryQuestionsUseCase.class));
    }
    @Test void adminCanDiscoverDraftAndStatusForwardsActorWithoutGeneratingOptions() throws Exception {
        var query = context.getBean(com.knowledgegym.content.application.QueryQuestionsUseCase.class);
        var catalog = context.getBean(com.knowledgegym.content.application.CatalogQueryUseCase.class);
        var choices = context.getBean(com.knowledgegym.content.domain.port.QuestionOptionRepository.class);
        var question = new com.knowledgegym.content.domain.model.Question();
        question.setId(target); question.setModuleId(actor); question.setTitle("Draft");
        question.setDifficulty(com.knowledgegym.shared.domain.model.Difficulty.MID);
        question.setContentStatus(com.knowledgegym.content.domain.model.Question.ContentStatus.DRAFT);
        when(query.executeAdmin(any())).thenReturn(new com.knowledgegym.shared.domain.model.PageResult<>(List.of(question),1,20,1));
        when(catalog.listModules(null)).thenReturn(List.of()); when(catalog.moduleSlugIndex()).thenReturn(java.util.Map.of());
        when(choices.findByQuestionIds(any())).thenReturn(java.util.Map.of());
        mvc.perform(get("/admin/content/questions").with(authentication(auth("ADMIN"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].contentStatus").value("DRAFT"))
            .andExpect(jsonPath("$.totalElements").value(1));
        verify(query).executeAdmin(any()); verify(query,never()).execute(any());
        var manage = context.getBean(com.knowledgegym.content.application.ManageQuestionsUseCase.class);
        question.setContentStatus(com.knowledgegym.content.domain.model.Question.ContentStatus.HIDDEN);
        when(manage.changeStatus(actor,target,question.getContentStatus(),"Review")).thenReturn(question);
        mvc.perform(patch("/admin/content/questions/" + target + "/status").with(authentication(auth("ADMIN")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIDDEN\",\"reason\":\"Review\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.contentStatus").value("HIDDEN"));
        verify(manage).changeStatus(actor,target,question.getContentStatus(),"Review");
        verifyNoInteractions(context.getBean(com.knowledgegym.content.application.GenerateQuestionOptionsUseCase.class));
        mvc.perform(patch("/admin/content/questions/" + target + "/status").with(authentication(auth("ADMIN")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PUBLISHED\",\"reason\":\" \"}")).andExpect(status().isBadRequest());
        verify(manage,times(1)).changeStatus(any(),any(),any(),any());
    }

    private UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(actor, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
    private AdminUserPort.UserView view() {
        return new AdminUserPort.UserView(target, "user@example.com", "User", UserRole.USER,
                false, true, "LOCAL", 0, Instant.EPOCH, Instant.EPOCH);
    }
    @Configuration @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config {
        @Bean ManageUsersUseCase users() { return mock(ManageUsersUseCase.class); }
        @Bean AdminUsersController controller(ManageUsersUseCase users) { return new AdminUsersController(users); }
        @Bean com.knowledgegym.blog.application.ModerateBlogUseCase moderation() {
            return mock(com.knowledgegym.blog.application.ModerateBlogUseCase.class);
        }
        @Bean com.knowledgegym.presentation.rest.blog.AdminModerationController moderationController(
                com.knowledgegym.blog.application.ModerateBlogUseCase service) {
            return new com.knowledgegym.presentation.rest.blog.AdminModerationController(service);
        }
        @Bean com.knowledgegym.learning.application.AdminLearningUseCase learning() {
            return mock(com.knowledgegym.learning.application.AdminLearningUseCase.class);
        }
        @Bean com.knowledgegym.presentation.rest.admin.AdminLearningController learningController(
                com.knowledgegym.learning.application.AdminLearningUseCase service) {
            return new com.knowledgegym.presentation.rest.admin.AdminLearningController(service);
        }
        @Bean com.knowledgegym.blog.application.GenerateLearningDraftUseCase drafts() { return mock(com.knowledgegym.blog.application.GenerateLearningDraftUseCase.class); }
        @Bean com.knowledgegym.presentation.rest.blog.AdminLearningDraftController draftsController(com.knowledgegym.blog.application.GenerateLearningDraftUseCase service) { return new com.knowledgegym.presentation.rest.blog.AdminLearningDraftController(service); }
        @Bean com.knowledgegym.content.application.QueryQuestionsUseCase questionQuery() { return mock(com.knowledgegym.content.application.QueryQuestionsUseCase.class); }
        @Bean com.knowledgegym.content.application.CatalogQueryUseCase catalog() { return mock(com.knowledgegym.content.application.CatalogQueryUseCase.class); }
        @Bean com.knowledgegym.content.domain.port.QuestionRepository questions() { return mock(com.knowledgegym.content.domain.port.QuestionRepository.class); }
        @Bean com.knowledgegym.content.domain.port.QuestionOptionRepository questionOptions() { return mock(com.knowledgegym.content.domain.port.QuestionOptionRepository.class); }
        @Bean com.knowledgegym.presentation.rest.content.AdminQuestionReadController questionReadController(
                com.knowledgegym.content.domain.port.QuestionRepository questions, com.knowledgegym.content.domain.port.QuestionOptionRepository choices,
                com.knowledgegym.content.application.QueryQuestionsUseCase query, com.knowledgegym.content.application.CatalogQueryUseCase catalog) {
            return new com.knowledgegym.presentation.rest.content.AdminQuestionReadController(questions,choices,query,catalog);
        }
        @Bean com.knowledgegym.content.application.ManageQuestionsUseCase manageQuestions() { return mock(com.knowledgegym.content.application.ManageQuestionsUseCase.class); }
        @Bean com.knowledgegym.content.application.GenerateQuestionOptionsUseCase generateChoices() { return mock(com.knowledgegym.content.application.GenerateQuestionOptionsUseCase.class); }
        @Bean com.knowledgegym.content.domain.port.ContentImportJob importJob() { return mock(com.knowledgegym.content.domain.port.ContentImportJob.class); }
        @Bean com.knowledgegym.presentation.rest.content.AdminContentController questionController(
                com.knowledgegym.content.domain.port.ContentImportJob job, com.knowledgegym.content.application.ManageQuestionsUseCase manage,
                com.knowledgegym.content.application.GenerateQuestionOptionsUseCase choices) {
            return new com.knowledgegym.presentation.rest.content.AdminContentController(job,manage,choices);
        }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().authenticated())
                    .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.setStatus(401)))
                    .build();
        }
    }
}
