package com.knowledgegym.english.application;

import com.knowledgegym.english.domain.model.EnglishExercise;
import com.knowledgegym.english.domain.model.EnglishExercisePart;
import java.util.ArrayList;
import java.util.List;
import static com.knowledgegym.english.domain.model.EnglishExercise.Skill.LISTENING;

/** Original format-count practice, not an institution recording or calibrated entrance score. */
final class EnglishHcmusListeningBank {
    private EnglishHcmusListeningBank() {}
    private static EnglishExercise.Item q(String id, String stem, int answer, String reason, String... options) {
        return new EnglishExercise.Item(id, stem, List.of(options), answer, reason);
    }
    private static final EnglishExercise SHORT = new EnglishExercise("hcmus-listening-short-v1", LISTENING,
        "Ten short conversations", "HCMUS preparation · Short conversations / 10 questions", 10, 0,
        "Listen to ten numbered conversations. Choose one answer for each conversation. These are independently authored practice situations, not recalled HCMUS questions. Replay is a learning aid.",
        "", "/english/audio/dialogue-hcmus-short-v1.mp3", """
        Conversation 1.
        Anna: Shall we meet in the cafe at three?
        Ben: It's being cleaned this afternoon. Let's use the library discussion room instead.
        Anna: Fine. I'll see you there at three.

        Conversation 2.
        Anna: Have you sent your application?
        Ben: Not yet. I've completed the form, but I still need a copy of my degree certificate.
        Anna: You can scan it at the office.

        Conversation 3.
        Anna: Did you enjoy the workshop?
        Ben: The examples were useful, but there wasn't enough time to ask questions.
        Anna: Perhaps we can email the organiser.

        Conversation 4.
        Anna: Are you taking the train tomorrow?
        Ben: I planned to, but the first service is too late. I've booked an earlier coach.
        Anna: That should get you there before the meeting.

        Conversation 5.
        Anna: The printer isn't working. Should I buy more paper?
        Ben: There's plenty of paper. The screen says the ink has run out.
        Anna: I'll ask for a replacement cartridge.

        Conversation 6.
        Anna: Can you return the book on Friday?
        Ben: I need it for my weekend assignment. Could I keep it until Monday?
        Anna: Yes, Monday is fine.

        Conversation 7.
        Anna: Why did you choose the evening course?
        Ben: I work during the day. The evening schedule lets me attend regularly without changing my shifts.
        Anna: That sounds practical.

        Conversation 8.
        Anna: Do we need to prepare a presentation for the first class?
        Ben: No. The lecturer only asked us to read a short article beforehand.
        Anna: I'll do that tonight.

        Conversation 9.
        Anna: Is the new study room always quiet?
        Ben: Usually, but yesterday a group was practising a talk there. I moved upstairs to concentrate.
        Anna: I'll check before choosing a desk.

        Conversation 10.
        Anna: I keep forgetting the new phrases. Should I copy the list again?
        Ben: Try closing the list and making a sentence from memory. Then check it and try again later.
        Anna: I'll give that a try.
        """, List.of(
            q("sc1", "Where will the speakers meet?", 1, "They agree on the library discussion room at three.", "At the cafe", "In the library discussion room", "At the cleaning office", "At the station"),
            q("sc2", "What does Ben still need for his application?", 3, "His form is complete, but he needs a degree-certificate copy.", "A new application form", "A train ticket", "A library card", "A copy of his degree certificate"),
            q("sc3", "What disappointed Ben about the workshop?", 0, "He says there was not enough time to ask questions.", "Limited time for questions", "The lack of examples", "The price of the ticket", "The evening schedule"),
            q("sc4", "How will Ben travel tomorrow?", 2, "He changed his original plan and booked an earlier coach.", "By train", "By bicycle", "By coach", "On foot"),
            q("sc5", "Why is the printer not working?", 1, "The display reports that its ink has run out; paper is available.", "There is no paper", "It needs more ink", "The screen is missing", "The office is closed"),
            q("sc6", "When will Ben return the book?", 3, "Anna agrees that he can keep it until Monday.", "On Thursday", "On Friday", "On Saturday", "On Monday"),
            q("sc7", "Why did Ben choose the evening course?", 0, "It fits around his daytime work without changing shifts.", "It fits his working hours", "It has no assignments", "It is the shortest course", "It is taught at his workplace"),
            q("sc8", "What should students do before the first class?", 2, "The lecturer asked them to read an article, not prepare a presentation.", "Record a talk", "Buy a printer", "Read a short article", "Write a full research report"),
            q("sc9", "Why did Ben move upstairs?", 1, "A group was practising a talk and he wanted to concentrate.", "He needed to borrow equipment", "He wanted a quieter place", "The room had no desks", "A lecturer asked him to leave"),
            q("sc10", "What does Ben recommend?", 3, "He recommends producing a sentence from memory, checking it and revisiting later.", "Copying the list without checking", "Avoiding unfamiliar phrases", "Memorising only the first word", "Practising recall and checking it later")
        ), List.of("Notice changes from an initial plan.", "Listen for the reason, not just a familiar word.", "Answer from the recording, not assumptions about the real institution."));

    static List<EnglishExercise> exercises(List<EnglishExercise> base) {
        var source = base.stream().filter(e -> e.id().equals("listening-study-space-v1")).findFirst().orElseThrow();
        var talk = base.stream().filter(e -> e.id().equals("listening-urban-shade-v1")).findFirst().orElseThrow();
        var questions = new ArrayList<EnglishExercise.Item>();
        for (int i = 0; i < source.items().size(); i++) {
            var item = source.items().get(i);
            questions.add(new EnglishExercise.Item("lc" + (i + 1), item.stem(), item.options(), item.correctIndex(), item.explanation()));
        }
        questions.add(q("lc5", "What will Anna add to the shared document?", 0,
            "Anna says she will add a short agenda as well as a place for members' points.", "A short meeting agenda", "The room payment receipt", "The completed laboratory results", "A train timetable"));
        var longConversation = new EnglishExercise("hcmus-listening-long-v1", LISTENING, "A study meeting: five listening questions",
            "HCMUS preparation · Long conversation / 5 questions", 8, 0,
            "Listen to the original study-space conversation and answer five questions. The audio is shared with a shorter practice task; question IDs are distinct. Not an official HCMUS paper.",
            "", source.audioPath(), source.transcript(), List.copyOf(questions), source.checklist());
        var clips = List.of(SHORT, longConversation, talk);
        var complete = new EnglishExercise("hcmus-listening-complete-v1", LISTENING, "HCMUS listening-format practice: twenty questions",
            "HCMUS preparation · 10 + 5 + 5 questions", 20, 0,
            "Practise ten short conversations, a longer conversation with five questions, and a talk with five questions. Suggested practice time: approximately 20 minutes. Original HCMUS-format-count practice, not an institution paper, topic prediction or calibrated assessment. Replay controls are learning aids. This set reuses the individual recordings.",
            "", "/english/audio/hcmus-listening-complete-v1.mp3",
            String.join("\n\n", clips.stream().map(e -> e.title() + "\n" + e.transcript()).toList()),
            clips.stream().flatMap(e -> e.items().stream()).toList(),
            List.of("Short conversations: 10 questions.", "Long conversation: 5 questions.", "Talk: 5 questions.", "Raw correct counts do not convert to an official weighted entrance score."));
        return List.of(SHORT, longConversation, complete);
    }
    static List<EnglishExercisePart> parts(String id, List<EnglishExercise> all) {
        if (!id.equals("hcmus-listening-complete-v1")) return List.of();
        return List.of("hcmus-listening-short-v1", "hcmus-listening-long-v1", "listening-urban-shade-v1").stream().map(key -> {
            var e = all.stream().filter(exercise -> exercise.id().equals(key)).findFirst().orElseThrow();
            return new EnglishExercisePart(e.id(), e.focus() + ": " + e.title(), "", e.audioPath(), e.items().stream().map(EnglishExercise.Item::id).toList());
        }).toList();
    }
}
