package com.knowledgegym.blog.application;

import com.knowledgegym.blog.domain.model.BlogPost;
import com.knowledgegym.blog.domain.port.BlogPostRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlogPostsUseCaseTest {
    @Test
    void rejectsCommentThatSanitizesToBlank() {
        UUID postId = UUID.randomUUID();
        FakePosts posts = new FakePosts(postId);
        BlogPostsUseCase useCase = new BlogPostsUseCase(posts, html -> "");

        assertThatThrownBy(() -> useCase.addComment("slug", UUID.randomUUID(), null, "<script>x</script>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("comment");
        assertThat(posts.comments).isEmpty();
    }

    @Test
    void persistsSanitizedComment() {
        UUID postId = UUID.randomUUID();
        FakePosts posts = new FakePosts(postId);
        BlogPostsUseCase useCase = new BlogPostsUseCase(posts, html -> "safe text");

        var comment = useCase.addComment("slug", UUID.randomUUID(), null, "<b>safe text</b>");
        assertThat(comment.content()).isEqualTo("safe text");
        assertThat(posts.comments).hasSize(1);
    }

    @Test
    void pagedListReturnsZeroBasedPageWithTotal() {
        FakePosts posts = new FakePosts(UUID.randomUUID());
        BlogPostsUseCase useCase = new BlogPostsUseCase(posts, html -> html);

        var page = useCase.listPaged(0, 5, null);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(5);
        assertThat(page.totalElements()).isEqualTo(7);
        assertThat(page.items()).isEmpty();

        assertThatThrownBy(() -> useCase.listPaged(-1, 5, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.listPaged(0, 51, null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static final class FakePosts implements BlogPostRepository {
        private final UUID postId;
        private final List<Comment> comments = new ArrayList<>();
        private FakePosts(UUID postId) { this.postId = postId; }

        @Override public List<BlogPost> findPublished(int offset, int limit, String tag) { return List.of(); }
        @Override public long countPublished(String tag) { return 7L; }
        @Override public Optional<BlogPost> findPublishedBySlug(String slug) {
            return Optional.of(new BlogPost(postId, UUID.randomUUID(), "HUMAN", "T", slug, "b", null,
                    null, null, "PUBLISHED", Instant.now(), 0, 0, List.of(), Instant.now()));
        }
        @Override public Optional<BlogPost> findById(UUID id) { return findPublishedBySlug("x"); }
        @Override public BlogPost createDraft(UUID authorId, String title, String slug, String body, String excerpt,
                                              UUID moduleId, UUID questionId, List<String> tags) { throw new UnsupportedOperationException(); }
        @Override public void publish(UUID postId) {}
        @Override public List<Comment> comments(UUID postId) { return List.copyOf(comments); }
        @Override public Comment addComment(UUID postId, UUID userId, UUID parentId, String body) {
            Comment c = new Comment(UUID.randomUUID(), postId, userId, parentId, body, Instant.now(), null, null);
            comments.add(c);
            return c;
        }
        @Override public boolean setLiked(UUID postId, UUID userId, boolean liked) { return liked; }
        @Override public void recordView(UUID postId, UUID userId) {}
    }
}
