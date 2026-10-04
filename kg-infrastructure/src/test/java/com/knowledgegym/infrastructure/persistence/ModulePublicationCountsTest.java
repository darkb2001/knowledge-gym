package com.knowledgegym.infrastructure.persistence;

import com.knowledgegym.infrastructure.TestPostgres;
import com.knowledgegym.infrastructure.persistence.adapter.ModuleRepositoryAdapter;
import com.knowledgegym.infrastructure.persistence.entity.QuestionJpaEntity;
import com.knowledgegym.infrastructure.persistence.repository.SpringDataModuleRepository;
import com.knowledgegym.infrastructure.persistence.repository.SpringDataQuestionRepository;
import org.flywaydb.core.Flyway;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Real Spring Data queries + Hibernate + disposable PostgreSQL; not a full HTTP application. */
class ModulePublicationCountsTest {
    @Test
    void publishedCountsExcludeDraftHiddenArchivedWhileRawCountsRetainThem() {
        try (var database = new TestPostgres()) {
            database.start();
            var dataSource = database.dataSource();
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
            var jdbc = new JdbcTemplate(dataSource);
            UUID topic = UUID.randomUUID();
            UUID module = UUID.randomUUID();
            jdbc.update("INSERT INTO topics(id,name,slug) VALUES(?,'Counts','counts')", topic);
            jdbc.update("INSERT INTO modules(id,topic_id,name,slug) VALUES(?,?,'Counts','counts')", module, topic);
            int order = 1;
            for (String status : new String[]{"PUBLISHED", "DRAFT", "HIDDEN", "ARCHIVED"}) {
                jdbc.update("INSERT INTO questions(id,module_id,title,answer_html,difficulty,sort_order,content_status) "
                                + "VALUES(?,?,'Question','<p>Answer</p>','MID',?,?)",
                        UUID.randomUUID(), module, order++, status);
            }
            var notes = new com.knowledgegym.infrastructure.persistence.adapter.NoteRepositoryAdapter(
                    mock(com.knowledgegym.infrastructure.persistence.repository.SpringDataNoteRepository.class), jdbc);
            for (var row : jdbc.queryForList("SELECT id, content_status FROM questions WHERE module_id=?", module)) {
                UUID questionId = (UUID) row.get("id");
                assertThat(notes.questionExists(questionId)).isTrue();
                assertThat(notes.publishedQuestionExists(questionId))
                        .isEqualTo("PUBLISHED".equals(row.get("content_status")));
            }
            var registry = new StandardServiceRegistryBuilder()
                    .applySetting("hibernate.connection.datasource", dataSource).build();
            try (var factory = new MetadataSources(registry).addAnnotatedClass(QuestionJpaEntity.class)
                    .buildMetadata().buildSessionFactory(); var entityManager = factory.createEntityManager()) {
                var repository = new JpaRepositoryFactory(entityManager).getRepository(SpringDataQuestionRepository.class);
                var adapter = new ModuleRepositoryAdapter(mock(SpringDataModuleRepository.class), repository);
                assertThat(adapter.countQuestions(module)).isEqualTo(4);
                assertThat(adapter.countQuestionsByModule()).containsEntry(module, 4L);
                assertThat(adapter.countPublishedQuestions(module)).isEqualTo(1);
                assertThat(adapter.countPublishedQuestionsByModule()).containsEntry(module, 1L);
                assertThat(adapter.countPublishedQuestions(UUID.randomUUID())).isZero();
            } finally {
                StandardServiceRegistryBuilder.destroy(registry);
            }
        }
    }
}
