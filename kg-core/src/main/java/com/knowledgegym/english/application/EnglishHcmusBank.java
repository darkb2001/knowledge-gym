package com.knowledgegym.english.application;

import com.knowledgegym.english.domain.model.EnglishExercise;
import com.knowledgegym.english.domain.model.EnglishReferenceResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static com.knowledgegym.english.application.EnglishReferenceResponses.model;
import static com.knowledgegym.english.application.EnglishReferenceResponses.note;
import static com.knowledgegym.english.domain.model.EnglishExercise.Skill.*;

/** Independently authored preparatory tasks, not HCMUS papers, calibrated scores or a full mock. */
final class EnglishHcmusBank {
    private EnglishHcmusBank() {}
    private static EnglishExercise.Item q(String id, String stem, int answer, String why, String... options) {
        return new EnglishExercise.Item(id, stem, List.of(options), answer, why);
    }
    private static EnglishExercise objective(String id, EnglishExercise.Skill skill, String title, String focus, int minutes, String passage, EnglishExercise.Item... items) {
        return new EnglishExercise(id, skill, title, focus, minutes, 0,
            "Choose the best answer. This is original targeted practice informed by the published HCMUS postgraduate entrance format, not an official paper or a prediction of examination topics.",
            passage, "", "", List.of(items), List.of("Read the whole sentence before choosing.", "Review the reason for each answer after submission.", "Raw correct counts are not the institution's weighted examination score."));
    }
    static final List<EnglishExercise> EXERCISES = List.of(
        objective("hcmus-vocabulary-v1", READING, "Vocabulary in everyday study", "HCMUS preparation · Vocabulary / 10 questions", 10, "",
            q("v1", "Please ___ your application before the deadline.", 1, "Submit means formally send an application for consideration.", "borrow", "submit", "repair", "invite"),
            q("v2", "The instructions were ___, so everyone understood what to do.", 2, "Clear describes instructions that are easy to understand.", "crowded", "expensive", "clear", "heavy"),
            q("v3", "We need to ___ the two options before choosing one.", 0, "Compare means examine similarities and differences.", "compare", "deliver", "forget", "lend"),
            q("v4", "The report is based on ___ collected during the project.", 3, "Evidence supports the statements in a report.", "furniture", "luggage", "weather", "evidence"),
            q("v5", "This bus service is ___: it usually arrives on time.", 1, "Reliable describes something you can depend on.", "temporary", "reliable", "private", "narrow"),
            q("v6", "I could not attend the meeting, but my colleague sent me a ___ of the discussion.", 0, "A summary gives the main points briefly.", "summary", "receipt", "recipe", "ticket"),
            q("v7", "Students can ___ books from the library for two weeks.", 2, "Borrow means take something temporarily and return it; lend is the giver's action.", "lend", "owe", "borrow", "spend"),
            q("v8", "The workshop will ___ practical examples as well as a short talk.", 3, "Include means have something as part of a whole.", "prevent", "refuse", "escape", "include"),
            q("v9", "We should ___ the results to see whether the new method helped.", 1, "Evaluate means assess value or effectiveness.", "decorate", "evaluate", "cancel", "pack"),
            q("v10", "The new timetable gives students more ___ to choose when to study.", 0, "Flexibility is the ability to adapt or make choices within an arrangement.", "flexibility", "pollution", "permission slips", "equipment")),
        objective("hcmus-grammar-v1", WRITING, "Sentence completion: grammar foundations", "HCMUS preparation · Sentence completion / 15 questions", 15, "",
            q("g1", "She ___ at this college since 2022.", 2, "Present perfect connects a situation starting in the past with now; since gives its starting point.", "works", "worked", "has worked", "is work"),
            q("g2", "If I have time tomorrow, I ___ the library.", 0, "The first conditional uses present simple in the if clause and will in the result clause.", "will visit", "visited", "would visited", "had visited"),
            q("g3", "The report ___ by the committee yesterday.", 3, "Was reviewed is past-simple passive: the report receives the action.", "reviews", "has reviewing", "reviewed", "was reviewed"),
            q("g4", "This exercise is ___ than the previous one.", 1, "A longer adjective forms the comparative with more, followed by than.", "most useful", "more useful", "usefully", "usefulest"),
            q("g5", "There isn't ___ information in the message.", 2, "Much modifies uncountable information in a negative sentence.", "many", "a few", "much", "several"),
            q("g6", "I enjoy ___ with classmates after the lesson.", 0, "Enjoy is followed by the -ing form.", "studying", "to study", "study", "studied"),
            q("g7", "The lecturer asked us ___ the instructions carefully.", 3, "Ask someone to do something takes an object and to-infinitive.", "read", "reading", "reads", "to read"),
            q("g8", "Although it was raining, ___ went ahead.", 1, "Although introduces a concessive clause; the main clause does not also need but.", "but the visit", "the visit", "because the visit", "so that the visit"),
            q("g9", "The student ___ project won a prize thanked her supervisor.", 0, "Whose introduces possession in a relative clause.", "whose", "which", "where", "what"),
            q("g10", "There is no requirement to provide two copies. You ___ submit two copies; one is enough.", 2, "Don't have to expresses lack of necessity, not prohibition.", "mustn't", "can't", "don't have to", "shouldn't have"),
            q("g11", "By the time we arrived, the talk ___.", 3, "Past perfect describes an event completed before another past event.", "starts", "has started", "is starting", "had started"),
            q("g12", "The course is suitable ___ people with little experience.", 1, "Suitable for is the appropriate adjective-preposition combination.", "at", "for", "from", "by"),
            q("g13", "In formal written English: Neither of the two answers ___ correct.", 0, "In formal standard usage neither of takes a singular verb here.", "is", "are", "have", "were being"),
            q("g14", "He speaks ___ enough for everyone to understand.", 2, "Clearly is an adverb modifying speaks; enough follows the adverb.", "clear", "clearness", "clearly", "clearest"),
            q("g15", "She said that she ___ the next day.", 3, "In this reported past statement, would corresponds to will and the next day to tomorrow.", "arrives", "has arrived", "will arriving", "would arrive")),
        objective("hcmus-cloze-v1", WRITING, "Cloze: organising a study group", "HCMUS preparation · Cloze / 10 questions", 12, """
            When our course began, I decided to join a study group (1) ____ I found some topics difficult. We arranged to meet once a week in a room (2) ____ was quiet enough for discussion. Before each meeting, every member prepared (3) ____ short explanation of one idea.

            At first, we spent too much time rereading our notes. (4) ____, we changed our approach. We asked one another questions and checked answers (5) ____ a reliable source. If someone made a mistake, the others helped them understand (6) ____ it had happened. Nobody was expected to know everything.

            The group became (7) ____ useful when we agreed on a clear plan. We also learned to listen carefully instead of interrupting. By the end of the term, we (8) ____ developed a routine that suited us. Working together did not replace individual study, (9) ____ it helped us notice gaps in our understanding. I would recommend trying a small group, provided that everyone is willing (10) ____ prepare and contribute.
            """,
            q("c1", "Choose the word for gap 1.", 1, "Because introduces the reason for joining.", "although", "because", "unless", "despite"),
            q("c2", "Choose the word for gap 2.", 0, "Which is the subject of the relative clause describing the room.", "which", "where", "who", "whose"),
            q("c3", "Choose the word for gap 3.", 2, "A singular countable explanation needs an article; short starts with a consonant sound.", "an", "some", "a", "many"),
            q("c4", "Choose the word for gap 4.", 3, "Therefore signals the change made as a result of the initial problem.", "Otherwise", "For instance", "Similarly", "Therefore"),
            q("c5", "Choose the word for gap 5.", 1, "Check against a source means compare an answer with it.", "between", "against", "during", "until"),
            q("c6", "Choose the word for gap 6.", 0, "Why introduces the reason for the mistake.", "why", "where", "whose", "whether or not that"),
            q("c7", "Choose the word for gap 7.", 2, "More forms the comparative of useful.", "much", "most", "more", "many"),
            q("c8", "Choose the word for gap 8.", 3, "Had developed looks back from a point at the end of the term.", "have", "are", "were", "had"),
            q("c9", "Choose the word for gap 9.", 1, "But contrasts not replacing individual work with providing a benefit.", "so", "but", "or else", "because of"),
            q("c10", "Choose the word for gap 10.", 0, "Willing is followed by a to-infinitive.", "to", "for", "at", "by")),
        new EnglishExercise("hcmus-transformations-v1", WRITING, "Keep the meaning, change the sentence", "HCMUS preparation · Sentence transformation / 5 prompts", 15, 0,
            "Rewrite each sentence using the beginning provided, keeping the original meaning. Number your answers 1–5. More than one correct formulation may be possible; compare the models after submission, without an automatic grammar score. 1. The laboratory closes at six. → The laboratory is not ... 2. The train was delayed, so I missed the beginning of the talk. → Because ... 3. Someone has changed the meeting time. → The meeting time ... 4. 'Please send the form today,' the coordinator said to me. → The coordinator asked ... 5. I started studying English three years ago and I still study it. → I have ...",
            "", "", "", List.of(), List.of("Keep tense, meaning and necessary details.", "Complete the supplied beginning grammatically.", "A model is one possible answer, not an exhaustive accepted-answer list.")),
        new EnglishExercise("hcmus-speaking-v1", SPEAKING, "Introduce yourself and develop a conversation", "HCMUS preparation · Self-introduction & guided conversation", 10, 0,
            "Practise a brief self-introduction: your background, present work or study, and why you want to pursue postgraduate study. Then answer these guided practice questions: What do you enjoy about your work or studies? How do you manage your time? Describe a challenge you have learned from. What would you like to improve in your English? How could further study help you? These are original practice topics, not a confirmed institution question bank. Record yourself and save a reflection, not a transcript upload.",
            "", "", "", List.of(), List.of("Use true details rather than memorising the model biography.", "Give a direct answer, a reason and an example when relevant.", "Respond to follow-up questions naturally; do not force an essay or a VSTEP Part 2 solution template."))
    );
    private static final Map<String, EnglishReferenceResponse> MODELS = Map.of(
        "hcmus-transformations-v1", model("Possible transformations and why they work", """
            1. The laboratory is not open after six.
            This keeps the closing-time meaning. The word 'after' is important; 'before six' would reverse it.

            2. Because the train was delayed, I missed the beginning of the talk.
            The because clause gives the cause. The main clause preserves the original result and past tense.

            3. The meeting time has been changed.
            The present-perfect passive preserves 'has changed'. The unspecified agent does not need to be invented.

            4. The coordinator asked me to send the form that day.
            'Asked me to' reports a request. In a past reporting context, 'that day' corresponds to 'today'. A different reporting context may retain 'today'.

            5. I have been studying English for three years.
            The present perfect continuous expresses an activity that began in the past and continues. 'I have studied English for three years' may also be appropriate. 'Since three years' is not the correct duration expression.
            """, note("Đối chiếu thì, câu bị động, từ nối nguyên nhân và cấu trúc tường thuật. Các cách diễn đạt tương đương có thể đúng; hệ thống không chấm bằng so khớp chuỗi.", "Compare tense, passive voice, causal linking and reported requests. Equivalent formulations can be correct; the system does not grade by exact-string matching.")),
        "hcmus-speaking-v1", model("A practice introduction and guided answers", """
            Self-introduction
            My name is Linh. I studied information systems and now work with a small team that supports online services. I enjoy understanding a problem and explaining a practical solution. I would like to pursue postgraduate study so that I can develop stronger research skills and examine ideas more carefully. Outside work, I like walking and reading. I am also working on my English because I want to participate more confidently in discussions and understand material from different sources.

            What do you enjoy about your work or studies?
            I enjoy finding out why a problem occurs. When our team receives a question, we first need to understand the situation rather than suggest a solution immediately. I find it satisfying when a clear explanation helps someone continue their work.

            How do you manage your time?
            I make a short list of priorities at the beginning of the day. I try to leave some time for unexpected tasks, because a plan that fills every minute is difficult to keep. For studying, a regular short session works better for me than waiting for a completely free weekend.

            Describe a challenge you have learned from.
            I once explained a process using too many technical terms. The listener became confused, so I had to begin again with a simpler example. It taught me to check what someone already knows and to ask whether an explanation is clear.

            What would you like to improve in your English?
            I would like to respond more naturally without translating every sentence first. Recording a short answer helps me notice where I pause or repeat myself. I can then practise the same idea using simpler language.

            How could further study help you?
            It could help me evaluate evidence and organise a longer project. I also hope to learn from people with different experiences. Those benefits depend on how actively I take part, not simply on obtaining another qualification.
            """, note("Đây là nhân vật minh hoạ, không phải tiểu sử của bạn hoặc đề thật. Thay bằng thông tin đúng về mình; mỗi câu trả lời có ý chính và phần giải thích ngắn.", "This is an illustrative biography, not yours or an actual examination paper. Use your true details; each answer has a direct point and a short explanation."))
    );
    static Optional<EnglishReferenceResponse> reference(String id) { return Optional.ofNullable(MODELS.get(id)); }
}
