package com.knowledgegym.blog.application;

import com.knowledgegym.blog.domain.model.CollectedItem;
import com.knowledgegym.blog.domain.model.CollectorSource;
import com.knowledgegym.blog.domain.port.CollectorFeed;
import com.knowledgegym.blog.domain.port.CollectorRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CollectItemsUseCaseTest {
    @Test
    void persistsNormalizedHashedAndRankedItemsAndRecordsRun() {
        UUID sourceId=UUID.randomUUID();
        FakeRepository repository=new FakeRepository(List.of(new CollectorSource(sourceId,"feed",CollectorSource.Type.RSS,"https://example.test/feed",null,60,null)));
        CollectorFeed feed=source->List.of(new CollectedItem("Java security release","https://example.test/1","Spring update",null,Instant.now(),List.of("java"),0,null));

        var result=new CollectItemsUseCase(repository,feed).execute();

        assertThat(result.sourcesFetched()).isEqualTo(1);
        assertThat(result.itemsInserted()).isEqualTo(1);
        assertThat(repository.saved).hasSize(1);
        assertThat(repository.saved.getFirst().contentHash()).hasSize(64);
        assertThat(repository.saved.getFirst().score()).isGreaterThan(40);
        assertThat(repository.saved.getFirst().category()).isEqualTo("spring");
        assertThat(repository.finishedSuccess).isTrue();
    }

    @Test
    void aFailingSourceDoesNotBlockOtherSources() {
        UUID failed=UUID.randomUUID(),good=UUID.randomUUID();
        FakeRepository repository=new FakeRepository(List.of(
                new CollectorSource(failed,"bad",CollectorSource.Type.RSS,"https://bad.test",null,60,null),
                new CollectorSource(good,"good",CollectorSource.Type.RSS,"https://good.test",null,60,null)));
        CollectorFeed feed=source->{if(source.id().equals(failed))throw new IllegalStateException("feed unavailable");return List.of();};

        var result=new CollectItemsUseCase(repository,feed).execute();

        assertThat(result.sourcesFetched()).isEqualTo(2);
        assertThat(result.failedSources()).isEqualTo(1);
        assertThat(repository.fetched).containsExactly(good);
        assertThat(repository.finishedSuccess).isFalse();
        assertThat(repository.error).contains("bad");
    }

    private static final class FakeRepository implements CollectorRepository {
        private final List<CollectorSource> sources; private final List<CollectedItem> saved=new ArrayList<>();
        private final List<UUID> fetched=new ArrayList<>(); private boolean finishedSuccess; private String error;
        private FakeRepository(List<CollectorSource> sources){this.sources=sources;}
        public List<CollectorSource> findDueSources(){return sources;}
        public int saveCollected(UUID sourceId,List<CollectedItem> items){
            saved.addAll(items);
            markFetched(sourceId);
            return items.size();
        }
        public void markFetched(UUID sourceId){fetched.add(sourceId);}
        public void scoreItem(UUID itemId){}
        public UUID startRun(String input){return UUID.randomUUID();}
        public void finishRun(UUID id,boolean success,String output,String error){this.finishedSuccess=success;this.error=error;}
    }
}
