package com.knowledgegym.infrastructure.blog;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.knowledgegym.blog.domain.model.CollectedItem;
import com.knowledgegym.blog.domain.model.CollectorSource;
import com.knowledgegym.blog.domain.port.CollectorFeed;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class ConfiguredCollectorFeed implements CollectorFeed {
    private final ObjectMapper mapper;
    public ConfiguredCollectorFeed(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public List<CollectedItem> fetch(CollectorSource source) throws Exception {
        URI uri = URI.create(source.url());
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Collector sources must use HTTPS");
        return switch (source.type()) {
            case RSS -> fetchRss(uri.toURL().openConnection());
            case API -> fetchGithubApi(uri.toURL());
            case SCRAPE -> throw new IllegalArgumentException("SCRAPE sources are deferred in M9");
        };
    }

    private List<CollectedItem> fetchRss(URLConnection connection) throws Exception {
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(10_000);
        connection.setRequestProperty("User-Agent", "KnowledgeGymCollector/1.0 (+https://knowledge-gym.local)");
        try (XmlReader reader = new XmlReader(connection.getInputStream())) {
            var feed = new SyndFeedInput().build(reader);
            List<CollectedItem> items = new ArrayList<>();
            for (SyndEntry entry : feed.getEntries()) {
                if (entry.getLink() == null || entry.getTitle() == null) continue;
                String summary = entry.getDescription() == null ? "" : Jsoup.clean(entry.getDescription().getValue(), Safelist.none());
                Instant published = entry.getPublishedDate() == null ? null : entry.getPublishedDate().toInstant();
                items.add(new CollectedItem(entry.getTitle().trim(), entry.getLink(), summary, null, published,
                        entry.getCategories().stream().map(c -> c.getName()).filter(n -> n != null && !n.isBlank()).limit(12).toList(), 0, null));
                if (items.size() >= 50) break;
            }
            return items;
        }
    }

    private List<CollectedItem> fetchGithubApi(java.net.URL url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(10_000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "KnowledgeGymCollector/1.0");
        try (var input = connection.getInputStream()) {
            JsonNode root = mapper.readTree(input);
            List<CollectedItem> items = new ArrayList<>();
            if (!root.isArray()) return List.of();
            for (JsonNode release : root) {
                String title = release.path("name").asText(release.path("tag_name").asText(""));
                String link = release.path("html_url").asText("");
                if (title.isBlank() || link.isBlank()) continue;
                String summary = Jsoup.clean(release.path("body").asText(""), Safelist.none());
                Instant published = release.path("published_at").isTextual() ? Instant.parse(release.path("published_at").asText()) : null;
                items.add(new CollectedItem(title, link, summary, null, published, List.of("github", "release"), 0, null));
                if (items.size() >= 50) break;
            }
            return items;
        } finally { connection.disconnect(); }
    }
}
