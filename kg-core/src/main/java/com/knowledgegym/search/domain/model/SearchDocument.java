package com.knowledgegym.search.domain.model;

import java.util.Set;
import java.util.UUID;

/**
 * A searchable document projected from Postgres into the search index.
 *
 * <p>Two text fields on purpose, and the design of both matters:
 *
 * <ul>
 *   <li>{@link #rawText()} is the human-readable text, indexed with a standard analyzer. It
 *       preserves English substring behaviour ("transaction" matching "transactions").
 *   <li>{@link #normalizedText()} is the **pre-normalised token stream** produced by
 *       {@code SearchText.build}: diacritics stripped, Vietnamese and English stopwords
 *       removed, 2–3 word n-grams joined with hyphens, and the VI–EN synonym table expanded.
 *
 * </ul>
 *
 * <p>The second field is what makes Vietnamese search work at all. PostgreSQL's {@code simple}
 * text search configuration only lowercases and tokenizes — it has no concept of "không" being
 * a stopword or "sao lưu" being the same concept as "backup". Reimplementing that logic as an
 * Elasticsearch analyzer would mean porting the stopword list, the diacritic folding and the
 * synonym table into a second language, and the two would drift. Indexing the already-computed
 * tokens keeps one implementation of the language rules, in {@code SearchText}, and lets
 * Elasticsearch contribute ranking and scale rather than linguistics.
 *
 * @param ownerId only set for notes: a note is visible to its owner alone, so the query always
 *                filters on this. Questions and published posts are world-readable and leave it
 *                null.
 * @param published only set for blog posts; a non-published post must not be searchable.
 */
public record SearchDocument(
        DocType type,
        UUID id,
        String title,
        String excerpt,
        String rawText,
        String normalizedText,
        UUID ownerId,
        Boolean published,
        String updatedAt) {

    /** The corpus is fixed: these are the three tables global search spans. */
    public enum DocType {
        QUESTION("question"),
        NOTE("note"),
        BLOG("blog");

        private final String wireName;

        DocType(String wireName) {
            this.wireName = wireName;
        }

        /** Matches the `type` value the existing `GET /search` contract returns. */
        public String wireName() {
            return wireName;
        }
    }

    /** Field names shared between the index mapping and the query builder. */
    public static final class Fields {
        public static final String TYPE = "type";
        public static final String TITLE = "title";
        public static final String EXCERPT = "excerpt";
        public static final String RAW = "raw";
        public static final String NORMALIZED = "normalized";
        public static final String OWNER = "ownerId";
        public static final String PUBLISHED = "published";
        public static final String UPDATED_AT = "updatedAt";

        private Fields() {
        }
    }

    /** Document ids are salted with the type so three tables can share one index. */
    public static String documentId(DocType type, UUID id) {
        return type.wireName() + ":" + id;
    }

    public String documentId() {
        return documentId(type, id);
    }

    /** The types a caller may see; kept next to the enum so the two cannot drift. */
    public static Set<DocType> allTypes() {
        return Set.of(DocType.QUESTION, DocType.NOTE, DocType.BLOG);
    }
}
