# Data Collector Agent — Thu thập dữ liệu từ Internet

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Blog Agent System                             │
│                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌────────────────┐  │
│  │  Data Collector  │  │  Content Engine  │  │  Publisher     │  │
│  │  (Thu thập data) │─▶│  (Viết bài)     │─▶│  (Đăng bài)    │  │
│  └────────┬────────┘  └────────┬────────┘  └───────┬────────┘  │
│           │                    │                    │           │
│  ┌────────▼────────┐  ┌───────▼─────────┐  ┌──────▼────────┐  │
│  │ Sources:         │  │ AI Models:       │  │ Channels:      │  │
│  │ • Stack Overflow │  │ • GPT-4o-mini    │  │ • Blog page    │  │
│  │ • Reddit r/java  │  │ • Claude 3.5     │  │ • RSS feed     │  │
│  │ • GitHub Trending│  │ • Gemini Flash   │  │ • Twitter/X    │  │
│  │ • Dev.to / Medium│  │                  │  │ • LinkedIn     │  │
│  │ • Baeldung       │  │ Templates:       │  │ • Email digest │  │
│  │ • YouTube (trans)│  │ • Deep Dive      │  │ • Telegram     │  │
│  │ • Java News      │  │ • Comparison     │  │                │  │
│  │ • Official Docs  │  │ • Tutorial       │  │                │  │
│  └─────────────────┘  └──────────────────┘  └───────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

**Phase boundary:** M9 implements collection, deterministic ranking, and the blog/publishing foundation; it does not call an AI model. AI drafting begins in M10 and every draft requires human review. Telegram is a delivery or command channel, not a data source. The existing bot stays an external service: integrate through an authenticated HTTP contract after its role is confirmed, without sharing the app database or exposing Kafka to its LXC.

## Nguồn dữ liệu

```
📰 News & Trends
├── Reddit r/java, r/learnjava, r/springboot
├── Hacker News (Java tag)
├── DZone Java
├── InfoQ Java
└── dev.java (Oracle official)

📚 Technical Deep Dives
├── Baeldung (RSS)
├── Baeldung Weekly (newsletter)
├── Dev.to (java, spring tags)
├── Medium (java-programming)
└── Vlad Mihalcea blog (Hibernate/JPA)

💻 Code & Trends
├── GitHub Trending (language:Java)
├── GitHub Releases (spring-boot, hibernate)
└── Maven Central (new popular libraries)

🎥 Video Content
├── YouTube (Java Brains, Amigoscode, Fireship)
└── JavaOne / Devoxx talks

📋 Q&A / Interview
├── Stack Overflow (top java questions weekly)
├── LeetCode discussions
└── Glassdoor interview reviews
```

## Collector Pipeline

```python
class DataCollectorAgent:
    def run(self):
        # 1. Thu thập từ nhiều nguồn song song
        raw_items = []
        for source in self.active_sources:
            items = source.fetch(since=self.last_run, limit=50)
            raw_items.extend(items)
        
        # 2. Dedup (trùng lặp)
        unique = self.dedup(raw_items, strategy=[
            "url_exact", "title_similarity>0.8", "content_hash"
        ])
        
        # 3. Score & Rank
        scored = self.score(unique, criteria={
            "relevance_to_java": 0.3,
            "freshness": 0.2,
            "engagement": 0.2,
            "depth": 0.15,
            "interview_relevance": 0.15
        })
        
        # 4. Categorize
        categorized = self.categorize(scored, taxonomy={
            "java-core": ["jvm", "collections", "streams"],
            "spring": ["boot", "security", "data"],
            "database": ["jpa", "hibernate", "sql"],
            "microservices": ["kafka", "docker", "k8s"],
            "career": ["interview", "resume", "salary"]
        })
        
        # 5. Store
        self.db.upsert(categorized)
```

## Source Implementations

### RSS (Baeldung, Dev.to, DZone)

```java
// Rome RSS library
@Component
public class RSSSource implements CollectorSource {
    public List<CollectedItem> fetch(Instant since, int limit) {
        SyndFeed feed = new SyndFeedInput().build(new XmlReader(url));
        return feed.getEntries().stream()
            .filter(e -> e.getPublishedDate().toInstant().isAfter(since))
            .limit(limit)
            .map(this::toItem)
            .toList();
    }
}
```

### Reddit

```java
// JRAW library
@Component
public class RedditSource implements CollectorSource {
    public List<CollectedItem> fetch(Instant since, int limit) {
        return reddit.subreddit("java")
            .posts()
            .sorting(SubredditSort.TOP)
            .timePeriod(TimePeriod.WEEK)
            .limit(limit)
            .build()
            .accumulate()
            .stream()
            .map(this::toItem)
            .toList();
    }
}
```

### GitHub Trending

```java
// Jsoup scrape
@Component
public class GitHubTrendingSource implements CollectorSource {
    public List<CollectedItem> fetch(Instant since, int limit) {
        Document doc = Jsoup.connect("https://github.com/trending/java?since=weekly").get();
        return doc.select("article.Box-row").stream()
            .limit(limit)
            .map(repo -> new CollectedItem(
                repo.select("h2 a").text(),
                "https://github.com" + repo.select("h2 a").attr("href"),
                repo.select("p").text(),
                "github"
            ))
            .toList();
    }
}
```

### YouTube Transcript

```java
// Google API Client
@Component
public class YouTubeSource implements CollectorSource {
    public List<CollectedItem> fetch(Instant since, int limit) {
        // Search videos
        List<Video> videos = youtube.search()
            .list("snippet")
            .setQ("java interview 2026")
            .setType("video")
            .setOrder("viewCount")
            .setMaxResults((long) limit)
            .execute()
            .getItems();
        
        // Get transcripts
        return videos.stream()
            .map(v -> {
                String transcript = getTranscript(v.getId().getVideoId());
                return new CollectedItem(v, transcript);
            })
            .toList();
    }
}
```

## Topic Selection Strategy (4 strategies)

```java
@Service
public class TopicSelector {

    // Strategy 1: Trending từ collector
    // Strategy 2: Knowledge gap (ít bài viết)
    // Strategy 3: Seasonal (hiring season)
    // Strategy 4: User demand (câu hỏi được ôn nhiều)

    public Topic selectDailyTopic() {
        List<Candidate> candidates = new ArrayList<>();
        candidates.addAll(getTrendingCandidates());
        candidates.addAll(getGapCandidates());
        candidates.addAll(getSeasonalCandidates());
        candidates.addAll(getDemandCandidates());
        
        return candidates.stream()
            .filter(c -> !c.topic().equals(lastTopic)) // rotate
            .max(comparing(Candidate::priority))
            .orElseThrow();
    }
}
```

## DB Schema

```sql
collector_sources
├── id, name, type (RSS | API | SCRAPE)
├── url, config (JSONB), active
├── last_fetched_at, fetch_interval_sec (INT — đơn vị giây)

collected_items
├── id, source_id → collector_sources
├── title, url (UNIQUE), summary, content_hash VARCHAR(64)
├── score (DECIMAL), category, tags (TEXT[])
├── published_at, collected_at
├── used_in_post_id → blog_posts (nullable)
```

**4 topic-selection strategy** ở mục trên map vào `blog_generation_queue.selection_strategy`
(`TRENDING` / `GAP` / `SEASONAL` / `DEMAND`) — tách biệt với `writer_strategy`
(`AUTO` / `TEMPLATE` / `CURATED`) quyết định cách viết. Xem `07-erd.md`.

## Tech Stack cho Collector

| Component | Tech | Lý do |
|-----------|------|-------|
| RSS Parser | Rome (Java RSS library) | Mature, RSS/Atom |
| Web Scraping | Jsoup + Playwright | Static + dynamic |
| Reddit API | JRAW | Official wrapper |
| YouTube API | Google API Client | Transcript + metadata |
| AI Provider | OpenAI SDK / Anthropic SDK | GPT-4o-mini |
| Queue | Redis + Spring @Async | Job scheduling |
| Search | Elasticsearch (optional) | Full-text |
