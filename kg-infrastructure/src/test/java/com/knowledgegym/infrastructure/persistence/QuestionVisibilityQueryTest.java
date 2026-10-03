package com.knowledgegym.infrastructure.persistence;

import com.knowledgegym.content.domain.model.QuestionQuery;
import com.knowledgegym.infrastructure.persistence.dao.QuestionSearchDao;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** SQL construction regression; this is not a full-stack persistence test. */
class QuestionVisibilityQueryTest {
    @Test void adminAndPublicDataAndCountUseSeparateVisibilityScopes() {
        var em = mock(EntityManager.class);
        var nativeQuery = mock(Query.class);
        var sql = new ArrayList<String>();
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of());
        when(nativeQuery.getSingleResult()).thenReturn(0L);
        when(em.createNativeQuery(anyString(), any(Class.class))).thenAnswer(call -> {
            sql.add(call.getArgument(0)); return nativeQuery;
        });
        when(em.createNativeQuery(anyString())).thenAnswer(call -> {
            sql.add(call.getArgument(0)); return nativeQuery;
        });
        var dao = new QuestionSearchDao(); ReflectionTestUtils.setField(dao, "entityManager", em);
        var query = new QuestionQuery(null, null, null, null, 1, 20);
        dao.search(query); dao.count(query);
        assertEquals(2, sql.size());
        assertTrue(sql.stream().allMatch(text -> text.contains("q.content_status = 'PUBLISHED'")));
        sql.clear(); dao.search(query, true); dao.count(query, true);
        assertEquals(2, sql.size());
        assertTrue(sql.stream().noneMatch(text -> text.contains("content_status =")));
    }
}
