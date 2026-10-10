package com.knowledgegym.english.application;

import com.knowledgegym.english.domain.model.EnglishExercise;
import com.knowledgegym.english.domain.model.EnglishExercise.Item;
import com.knowledgegym.shared.application.NotFoundException;
import java.util.List;
import java.util.stream.Stream;
import com.knowledgegym.english.domain.model.EnglishExercisePart;
import static com.knowledgegym.english.domain.model.EnglishExercise.Skill.*;

/** Keep released IDs/content immutable: new authored revisions receive new IDs. */
public class EnglishCatalog {
    private static Item q(String id, String stem, List<String> options, int answer, String why) {
        return new Item(id, stem, options, answer, why);
    }
    private static final List<EnglishExercise> EXERCISES = List.of(
        new EnglishExercise("listening-announcement-v1", LISTENING, "A change of plans", "Part 1 · Announcement", 5, 0,
            "Listen to the library announcement. Choose the best answer to each question.", "",
            "/english/audio/announcement-v1.mp3",
            "Good morning, everyone. The university library will close at four this afternoon, two hours earlier than usual, because maintenance work is scheduled. Books due today can be returned tomorrow without a late fee. The study room in the science building will remain open until eight, but you will need your student card to enter. Please collect any reserved books before three thirty. Thank you for your understanding.",
            List.of(q("q1", "Why is the library closing early?", List.of("A staff meeting", "Maintenance work", "A public holiday", "A lack of visitors"), 1, "The announcement explicitly says maintenance work is scheduled."),
                q("q2", "What must students bring to the alternative study room?", List.of("A booking receipt", "A library book", "A student card", "A payment card"), 2, "Students need their student card to enter the science building study room."),
                q("q3", "When can books due today be returned without a fee?", List.of("Tomorrow", "Next week", "Only before four today", "At any time this month"), 0, "Books due today can be returned tomorrow without a late fee.")),
            List.of("Identify the purpose before focusing on details.", "Notice time changes and exceptions.")),
        new EnglishExercise("listening-dialogue-v1", LISTENING, "Planning a community event", "Part 2 · Conversation", 7, 0,
            "Listen to two organisers discussing an event. Choose the best answer.", "",
            "/english/audio/dialogue-v1.mp3",
            "Anna: We need to decide where to hold the weekend workshop. The park would be free, but the weather forecast says rain. Ben: The community hall is available on Sunday. It costs fifty pounds, though. Anna: Our budget can cover that. My main concern is that it only has twenty chairs. Ben: I can borrow ten more from the school. Anna: Great. Let's book the hall. Should we advertise on social media? Ben: Yes, but let's also put a notice in the local shop. Not everyone checks social media. Anna: I'll write the notice tonight. Can you contact the hall manager? Ben: Of course. I'll call tomorrow morning.",
            List.of(q("q1", "Why do the organisers reject the park?", List.of("It is too expensive", "It is too small", "Rain is expected", "It is closed"), 2, "The weather forecast says rain."),
                q("q2", "How will they solve the seating problem?", List.of("Limit attendance to twenty", "Borrow chairs from a school", "Buy ten chairs", "Ask people to stand"), 1, "Ben offers to borrow ten chairs from the school."),
                q("q3", "Why will they place a notice in a shop?", List.of("To reach people who do not use social media", "To reduce the hall fee", "To recruit shop staff", "To collect tickets"), 0, "Ben says not everyone checks social media.")),
            List.of("Track who agrees to do each action.", "Separate an initial suggestion from the final decision.")),
        new EnglishExercise("listening-talk-v1", LISTENING, "Why small habits last", "Part 3 · Short talk", 8, 0,
            "Listen to a short educational talk. Identify the argument and supporting detail.", "",
            "/english/audio/talk-v1.mp3",
            "Many people begin a new routine with an ambitious goal, such as reading a book every week. Yet enthusiasm alone rarely makes a habit last. Research on behaviour suggests that a reliable cue and a manageable action are more useful. For example, reading two pages after breakfast ties the activity to an existing routine. The small target reduces the effort needed to begin. This does not mean larger goals are unimportant. They provide direction, while the daily action provides consistency. It is also useful to prepare the environment: leave the book on the table rather than inside a cupboard. When a day is missed, the best response is to restart the next day, not to double the target as a punishment. A habit is a pattern built over time, not a test of perfection.",
            List.of(q("q1", "What is the speaker's main argument?", List.of("Ambition should be avoided", "Manageable actions and reliable cues support habits", "Reading is better than other habits", "Missing one day destroys a habit"), 1, "The talk prioritises a reliable cue and a manageable daily action over enthusiasm alone."),
                q("q2", "Why leave a book on the table?", List.of("To impress visitors", "To keep it clean", "To make the action easier to begin", "To remember its price"), 2, "Preparing the environment lowers the effort needed to start."),
                q("q3", "What does the speaker recommend after missing a day?", List.of("Restart the next day", "Double the target", "Choose another goal", "Stop tracking progress"), 0, "The speaker explicitly recommends restarting rather than punishment.")),
            List.of("Listen for the main claim and its examples.", "Notice contrasts such as 'yet' and 'while'.")),
        new EnglishExercise("reading-community-v1", READING, "The library beyond books", "Mini passage · Main idea & inference", 12, 0,
            "Read the passage and choose the best answer. This is a short practice passage, not a full 40-question test.",
            """
            When a small town considered closing its public library, residents were asked how they used the building. The council expected most answers to mention borrowing books. Instead, many residents described the library as a place to work, meet neighbours or use a reliable internet connection. A retired teacher explained that she visited twice a week to help adults complete online forms. A young business owner used a quiet desk there because her home was too crowded.

            The survey changed the discussion. Rather than asking whether the town still needed printed books, the council began asking what shared services the town lacked. It discovered that the nearest public computer room was a forty-minute bus ride away. The library was not simply duplicating services that could be found elsewhere.

            Volunteers proposed running evening workshops to attract more visitors. However, the librarian warned that relying entirely on volunteers could make opening hours unpredictable. She supported community involvement but argued that trained staff were necessary to protect personal information and maintain a dependable service.

            The council eventually kept the library open and introduced a six-month trial of two evening sessions each week. Attendance increased, though this alone did not prove that every new activity was worthwhile. The council agreed to collect feedback on which services residents actually valued before making a permanent decision. The trial showed that familiar institutions can acquire new roles, provided that changes respond to real needs rather than to fashionable ideas.
            """, "", "",
            List.of(q("q1", "What is the main purpose of the passage?", List.of("To argue that printed books are obsolete", "To show how a library adapted to local needs", "To describe how to start a business", "To criticise all council surveys"), 1, "The passage follows the discovery of community needs and a measured adaptation of the library."),
                q("q2", "Why is the journey to the computer room mentioned?", List.of("To recommend public transport", "To explain the library's unique local value", "To compare bus fares", "To show that residents dislike computers"), 1, "The distant alternative shows the library provides a service not readily available locally."),
                q("q3", "What concern did the librarian express?", List.of("Volunteers might borrow too many books", "Evening sessions would attract nobody", "A volunteer-only service might be unreliable", "Surveys should replace staff"), 2, "She warned that relying entirely on volunteers could make opening hours unpredictable."),
                q("q4", "What can be inferred about the council's final approach?", List.of("It treated attendance as conclusive proof", "It avoided collecting more evidence", "It made every change permanent immediately", "It preferred a trial followed by further evaluation"), 3, "The six-month trial and planned feedback indicate an evidence-led, provisional approach.")),
            List.of("Locate evidence for each answer.", "Distinguish what is stated from what can reasonably be inferred.")),
        new EnglishExercise("writing-email-v1", WRITING, "An invitation with a purpose", "Task 1 · Letter / email", 20, 120,
            "Your English-speaking friend Alex wants to visit your town next month. Write an email suggesting when to visit, describing two activities you could do together, and explaining what Alex should bring. Write at least 120 words.", "", "", "", List.of(),
            List.of("Address all three requested points.", "Use an appropriate greeting, tone and closing.", "Organise ideas into clear paragraphs.", "Check grammar, spelling and useful vocabulary.")),
        new EnglishExercise("writing-essay-v1", WRITING, "Learning wherever you are", "Task 2 · Essay", 40, 250,
            "Some people believe online learning should replace traditional classroom teaching. Others think face-to-face classes remain essential. Discuss both views and give your own opinion. Use reasons and examples. Write at least 250 words.", "", "", "", List.of(),
            List.of("Discuss both views and state a clear position.", "Develop arguments with relevant examples.", "Connect paragraphs logically.", "Check range and accuracy of grammar and vocabulary.")),
        new EnglishExercise("speaking-interaction-v1", SPEAKING, "Your everyday world", "Part 1 · Social interaction", 3, 0,
            "Answer these questions aloud. Topic 1: Learning. What do you enjoy learning? How do you usually study? What would you like to learn next? Topic 2: Your neighbourhood. What do you like about where you live? Is there anything you would change? Would you recommend it to a visitor?", "", "", "", List.of(),
            List.of("Answer directly, then add a reason or example.", "Speak clearly at a natural pace.", "Avoid reading a prepared script.")),
        new EnglishExercise("speaking-solutions-v1", SPEAKING, "Make a choice, make a case", "Part 2 · Solution discussion", 4, 0,
            "Your university wants to help new students make friends. Three options are suggested: a sports afternoon, a campus tour, or a cooking workshop. Choose the best option, explain why it is suitable, and explain why you did not choose the other two.", "", "", "", List.of(),
            List.of("State your choice clearly.", "Compare all three options.", "Support your choice with specific reasons.", "Use linking expressions without overusing them.")),
        new EnglishExercise("speaking-development-v1", SPEAKING, "A greener daily life", "Part 3 · Topic development", 5, 0,
            "Give a short talk about ways individuals can protect the environment. You may discuss transport, consumption and community activities, or use your own ideas. Follow-up questions: Which change is easiest for students? Should governments or individuals take more responsibility? Can technology solve environmental problems?", "", "", "", List.of(),
            List.of("Introduce the topic and develop several ideas.", "Support your points with examples.", "Conclude your talk.", "Respond to follow-up questions rather than repeating the talk."))
    );
    private static final List<EnglishExercise> LISTENING_BANK = EnglishListeningBank.exercises(EXERCISES);
    private static final List<EnglishExercise> BASE = Stream.of(EXERCISES, LISTENING_BANK,
        EnglishReadingBank.exercises(), EnglishSubjectiveBank.EXERCISES, EnglishCurrentThemeBank.EXERCISES, EnglishHcmusBank.EXERCISES).flatMap(List::stream).toList();
    private static final List<EnglishExercise> ALL = Stream.concat(BASE.stream(), EnglishHcmusListeningBank.exercises(BASE).stream()).toList();
    public List<EnglishExercise> list() { return ALL; }
    public String curriculum(String id) {
        get(id);
        return id.startsWith("hcmus-") ? "HCMUS_PREPARATION" : "VSTEP";
    }
    public String scope(String id) {
        get(id);
        if (EXERCISES.stream().anyMatch(e -> e.id().equals(id))) return "SHORT_PRACTICE";
        return parts(id).isEmpty() ? "TASK_PRACTICE" : "COMPLETE_SKILL";
    }
    public List<EnglishExercisePart> parts(String id) {
        get(id);
        var reading = EnglishReadingBank.parts(id);
        if (!reading.isEmpty()) return reading;
        var listening = EnglishListeningBank.parts(id, LISTENING_BANK);
        return listening.isEmpty() ? EnglishHcmusListeningBank.parts(id, ALL) : listening;
    }
    public EnglishExercise get(String id) {
        return ALL.stream().filter(e -> e.id().equals(id)).findFirst()
            .orElseThrow(() -> new NotFoundException("English exercise not found"));
    }
}
