package com.knowledgegym.english.application;

import com.knowledgegym.english.domain.model.EnglishExercise;
import com.knowledgegym.english.domain.model.EnglishReferenceResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import static com.knowledgegym.english.application.EnglishReferenceResponses.model;
import static com.knowledgegym.english.application.EnglishReferenceResponses.note;
import static com.knowledgegym.english.domain.model.EnglishExercise.Skill.*;

/** Original practice aligned only to current HCMUS task counts; not past-paper reuse or topic forecasts. */
final class EnglishHcmusExpansionBank {
    private EnglishHcmusExpansionBank() {}
    private record Row(String stem, int answer, String reason, List<String> options) {}
    private static Row r(String stem, int answer, String reason, String... choices) { return new Row(stem, answer, reason, List.of(choices)); }
    private static EnglishExercise task(String id, EnglishExercise.Skill skill, String title, int minutes, String passage, Row... rows) {
        var items = IntStream.range(0, rows.length).mapToObj(i -> {
            var row = rows[i]; return new EnglishExercise.Item("x" + (i + 1), row.stem(), row.options(), row.answer(), row.reason());
        }).toList();
        return new EnglishExercise(id, skill, title, "HCMUS original preparation · " + rows.length + " questions", minutes, 0,
            "Choose the best answer. Original practice aligned to current HCMUS task types; not a past examination paper, topic forecast or calibrated assessment. Explanations appear after submission.",
            passage, "", "", items, List.of("Use grammatical and contextual evidence.", "Check distractors, not just the first familiar word.", "Review errors after submission; correct counts are not an official weighted score."));
    }
    private static final String CLOZE_A = """
        A small community garden opened beside a library last year. Before planting, the organisers asked local residents (1) ___ they wanted to grow. Several people had never gardened before, (2) ___ the group offered an introductory session. Tools were kept in a locked cupboard and could be (3) ___ during supervised sessions. Each volunteer was responsible (4) ___ one small area, but members also helped each other. The team chose plants (5) ___ did not need much water. After three months, they compared their notes (6) ___ the original plan. Some plants grew well; others needed more shade. Instead of blaming volunteers, the organisers decided (7) ___ the planting arrangements. The garden has now become a place where neighbours can share practical (8) ___. Attendance is not compulsory, and members can choose sessions that fit their (9) ___. The group hopes to continue the project if enough people remain (10) ___ in it.
        """;
    private static final String CLOZE_B = """
        A repair workshop recently started offering evening sessions. Its organisers wanted to make the service available to people (1) ___ worked during the day. At first, they expected everyone to bring electrical equipment, (2) ___ many visitors arrived with torn bags or broken furniture instead. A volunteer asks each visitor to describe the problem before any work (3) ___. Safety is more important (4) ___ speed. Volunteers do not promise to repair every object; some items are too damaged to use (5) ___. Visitors are encouraged to watch so that they can learn (6) ___ the process. The team keeps a record of common problems and uses it to plan future training. Since the evening sessions began, more residents (7) ___ involved. The organisers are considering (8) ___ an extra session, but they first need enough volunteers. They believe that sharing skills can reduce waste and help people feel more (9) ___. However, the workshop is a community learning activity, not a guarantee that every repair will be (10) ___.
        """;
    private static final String WETLAND_PASSAGE = """
        A town council was deciding how to improve a wetland beside a busy walking route. Some residents wanted a cafe and brighter lighting throughout the area. Others worried that more visitors would disturb wildlife. Instead of immediately choosing either proposal, the council asked a small team to observe how the site was used. The team counted visitors at different times, spoke with residents and recorded areas where people left the marked path.

        Their observations challenged several early assumptions. Most visitors came for a short walk before work or after school, rather than for a whole afternoon. Some people avoided the route because the entrance sign was difficult to read. Others thought the wetland was closed to the public because a damaged gate had been left across the main entrance. These problems did not require building a new cafe. Clearer information and a repaired gate could improve access at relatively low cost.

        Wildlife protection still mattered. A narrow strip near the water was used by nesting birds, and walkers sometimes entered it without realising that it was sensitive. The team suggested keeping the existing route but adding a simple boundary and explaining why visitors should remain outside that strip. The proposal aimed to guide behaviour, not exclude people from the entire wetland. Bright lights near the nesting area were not recommended.

        The council approved a six-month trial of these smaller changes. It agreed to review visitor feedback and repeat observations before considering major construction. Counting the number of new signs would not show whether people understood them, so observers also checked how visitors responded. The team did not claim that its first survey represented every season or every resident. It argued that a limited trial, followed by a careful review, could reveal which changes were useful and which needed adjustment. The approach combined access with protection rather than treating them as goals that could never coexist.
        """;
    static final List<EnglishExercise> EXERCISES = List.of(
        task("hcmus-grammar-study-v1", WRITING, "Sentence completion: study and planning", 15, "",
            r("By the time we arrived yesterday, the talk ___.", 1, "The talk ended before a past arrival, so the earlier event takes past perfect.", "ends", "had ended", "has ending", "will end"),
            r("The student ___ notebook was missing contacted reception.", 2, "Whose introduces possession: the student's notebook.", "which", "who", "whose", "where"),
            r("Please turn off the lights before ___ the room.", 0, "Before as a preposition can be followed by the -ing form.", "leaving", "leave", "left", "to leaving"),
            r("If the office were open now, I ___ the form there.", 3, "An unreal present condition uses would plus the base verb.", "submit", "submitted", "had submit", "would submit"),
            r("The new timetable ___ on the noticeboard every Monday.", 1, "A timetable receives the action: present-simple passive is is posted.", "posts", "is posted", "is posting", "has posting"),
            r("There are ___ chairs for all twelve participants; nobody needs to stand.", 0, "Enough expresses sufficient quantity before a plural noun.", "enough", "much", "every", "a little"),
            r("I look forward to ___ from you.", 2, "To in look forward to is a preposition, followed by a gerund.", "hear", "heard", "hearing", "have hear"),
            r("The second route is not as long ___ the first.", 3, "The equal-comparison construction is as adjective as.", "than", "like", "that", "as"),
            r("No one was in the office ___ I called at noon yesterday.", 1, "When links the visit/call time with the past situation.", "unless", "when", "despite", "during"),
            r("The instructions were so clear ___ we finished without asking for help.", 0, "So adjective that introduces a result clause.", "that", "than", "as", "for"),
            r("She suggested ___ the survey on a smaller group first.", 2, "Suggest takes an -ing complement in this construction.", "test", "to testing", "testing", "tested"),
            r("The files must ___ before the computer is replaced.", 3, "Modal passive uses modal + be + past participle.", "back up", "backing up", "backed up", "be backed up"),
            r("I have not seen that lecturer ___ last semester.", 1, "Since marks the starting point of a period continuing to now.", "for", "since", "during", "until"),
            r("Although the room was small, ___ comfortable.", 0, "The second clause needs its subject and finite verb.", "it was", "being", "was it to", "its"),
            r("Would you mind ___ the window?", 2, "Mind takes a gerund in a polite request.", "close", "to close", "closing", "closed")),
        task("hcmus-grammar-community-v1", WRITING, "Sentence completion: community and services", 15, "",
            r("The appointment has been moved ___ Friday morning.", 2, "An appointment is moved to a new time or date.", "at", "by", "to", "since"),
            r("There is very ___ milk left; we should buy some.", 0, "Little expresses a small amount of an uncountable noun.", "little", "few", "many", "several"),
            r("This is the shop ___ I bought the replacement part.", 3, "Where introduces the location of the purchase.", "whose", "whom", "which it", "where"),
            r("Neither of these two answers ___ correct in formal written English.", 1, "Neither of takes a singular verb in the formal convention tested here.", "are", "is", "be", "were"),
            r("We avoided ___ during the busiest hour.", 2, "Avoid takes an -ing complement.", "travel", "to travel", "travelling", "travelled"),
            r("You ___ smoke here; smoking is prohibited.", 0, "Must not expresses prohibition, not absence of necessity.", "must not", "need not", "do not have to", "may"),
            r("The bag was ___ heavy for the child to lift.", 3, "Too adjective to expresses exceeding a practical limit.", "enough", "so much", "such", "too"),
            r("If she had brought the receipt, the shop ___ a refund yesterday.", 1, "An unreal past result takes would have + past participle.", "will offer", "would have offered", "offers", "had offering"),
            r("The road ___ when we drove past, so we took another route.", 2, "The ongoing past action received by the road uses was being repaired.", "is repairing", "repairs", "was being repaired", "has repair"),
            r("She asked me where I ___ the following day.", 0, "Reported future from a past reporting verb uses would in normal statement order.", "would go", "will I go", "do I go", "have went"),
            r("Despite ___ tired, he completed the form carefully.", 3, "Despite is a preposition, so being can introduce the adjective.", "he was", "was", "to be", "being"),
            r("The guide spoke slowly ___ everyone could follow the directions.", 1, "So that introduces the intended result/purpose clause.", "because of", "so that", "in spite", "instead"),
            r("Each participant ___ received a copy of the safety leaflet.", 2, "Each participant is singular and takes has.", "have", "are", "has", "were"),
            r("The more carefully you read, ___ mistakes you are likely to make.", 0, "The paired comparative uses fewer for countable mistakes.", "the fewer", "the less", "the little", "the few"),
            r("The technician advised us ___ the device until it had been checked.", 3, "Advise someone not to do something is the required pattern.", "not use", "do not using", "to not used", "not to use")),
        task("hcmus-cloze-garden-v1", WRITING, "Cloze: a community garden", 12, CLOZE_A,
            r("Gap 1", 0, "What introduces the object of wanted to grow.", "what", "which it", "where", "whose"),
            r("Gap 2", 2, "So connects the lack of experience with the resulting introductory session.", "although", "unless", "so", "despite"),
            r("Gap 3", 1, "Tools temporarily used and returned are borrowed.", "lent from", "borrowed", "spent", "owed"),
            r("Gap 4", 3, "The collocation is responsible for.", "at", "to", "with", "for"),
            r("Gap 5", 0, "That is the relative pronoun functioning as subject for plants.", "that", "whom", "where", "whose"),
            r("Gap 6", 2, "Compare one thing with another.", "of", "by", "with", "from"),
            r("Gap 7", 1, "Decide takes an infinitive complement.", "change", "to change", "changing", "changed"),
            r("Gap 8", 3, "Knowledge is the uncountable noun needed after practical.", "know", "known", "knowingly", "knowledge"),
            r("Gap 9", 0, "Sessions fit a person's schedule.", "schedule", "weight", "temperature", "height"),
            r("Gap 10", 2, "People remain interested in a project.", "interesting", "interest", "interested", "interestingly")),
        task("hcmus-cloze-repair-v1", WRITING, "Cloze: learning through repair", 12, CLOZE_B,
            r("Gap 1", 2, "Who introduces a relative clause about people.", "whose", "where", "who", "whom it"),
            r("Gap 2", 0, "But contrasts the organisers' expectation with what visitors brought.", "but", "because", "unless", "despite"),
            r("Gap 3", 3, "Before introduces the point when work starts; work is singular here.", "begin", "beginning", "to begin", "begins"),
            r("Gap 4", 1, "A comparative adjective is followed by than.", "as", "than", "that", "like"),
            r("Gap 5", 2, "The adverb safely modifies use.", "safe", "safety", "safely", "safer"),
            r("Gap 6", 0, "Learn from an experience or process.", "from", "at", "of", "by it"),
            r("Gap 7", 3, "Since the sessions began connects the change to now; residents is plural.", "becomes", "was become", "have becoming", "have become"),
            r("Gap 8", 1, "Consider takes an -ing complement.", "add", "adding", "to add", "added"),
            r("Gap 9", 2, "Capable is an adjective describing increased ability.", "capability", "capably", "capable", "capabilities"),
            r("Gap 10", 0, "Successful is the adjective complement after will be.", "successful", "success", "succeed", "successfully")),
        task("hcmus-vocabulary-context-v1", READING, "Vocabulary: context and word families", 10, "",
            r("The museum offers free ___ to students on Wednesdays.", 1, "Admission means permission to enter a venue.", "advice", "admission", "equipment", "permission slips only"),
            r("We need a ___ explanation, not a description full of unfamiliar terms.", 3, "Straightforward describes something clear and easy to follow.", "crowded", "broken", "distant", "straightforward"),
            r("Please ___ your booking if you can no longer attend.", 0, "Cancel means withdraw a booking or arrangement.", "cancel", "borrow", "raise", "recognise"),
            r("The two accounts ___; one says Tuesday and the other says Thursday.", 2, "Differ means be unlike each other.", "agree", "repair", "differ", "repeat exactly"),
            r("The project was delayed because the team lacked the necessary ___.", 1, "Resources are the materials, time or support needed for a project.", "habits", "resources", "holidays", "conclusions"),
            r("We should check whether the information is ___ before sharing it.", 3, "Accurate means correct and not misleading.", "crowded", "portable", "shallow", "accurate"),
            r("The new system is more ___; it uses less energy to do the same job.", 0, "Efficient describes achieving a result with less waste.", "efficient", "expensive", "uncertain", "distant"),
            r("The report's main ___ is that small changes should be tested first.", 2, "A conclusion expresses the finding reached from the discussion/evidence.", "equipment", "appointment", "conclusion", "permission"),
            r("Only ___ staff may enter the storage room; visitors need an escort.", 1, "Authorised means having official permission for the action.", "unrelated", "authorised", "delayed", "missing"),
            r("The team will ___ the old signs with clearer ones.", 3, "Replace means put something new in place of something old.", "depend", "refuse", "avoid", "replace")),
        task("hcmus-reading-wetland-v1", READING, "Reading: access and wetland protection", 15, WETLAND_PASSAGE,
            r("What is the passage mainly about?", 2, "It describes an evidence-led trial balancing access and protection.", "Why every wetland needs a cafe", "How to exclude all walkers", "Testing small improvements using evidence", "Why surveys never help planning"),
            r("Why did the council arrange observations?", 0, "It wanted to understand use before choosing a proposal.", "To understand the site before deciding", "To prove every visitor wanted a cafe", "To replace every resident's opinion", "To measure only the number of birds"),
            r("When did most visitors use the route?", 3, "The observations found short visits before work or after school.", "Only during the night", "For whole afternoons", "Only on public holidays", "Before work or after school"),
            r("Why did some residents think the site was closed?", 1, "A damaged gate had been left across the main entrance.", "A cafe had closed permanently", "A damaged gate blocked the entrance", "A survey banned visitors", "All signs prohibited walking"),
            r("What does 'These problems' in paragraph 2 refer to?", 2, "It refers to unclear entrance information and the obstructing gate.", "Every seasonal change", "The cafe's opening hours", "Unclear information and the damaged gate", "A lack of bright nesting-area lights"),
            r("What was the purpose of the simple boundary?", 0, "It would guide walkers away from the sensitive nesting strip.", "To protect a nesting area while retaining access", "To close the entire wetland", "To prevent the council from observing", "To mark a new cafe building"),
            r("Which change was not recommended near the nesting area?", 3, "The passage explicitly rejects bright lights near that area.", "A simple boundary", "An explanation for visitors", "Keeping walkers on the route", "Bright lighting"),
            r("What would happen after six months?", 1, "The council planned to review feedback and repeat observations.", "Major construction would automatically begin", "Feedback and observations would be reviewed", "The route would permanently close", "The team would stop gathering evidence"),
            r("Why was counting new signs insufficient?", 2, "It would not establish whether visitors understood or acted on them.", "Signs cannot ever be counted", "Every sign had identical wording", "Counts do not show understanding or behaviour", "Residents were forbidden to read signs"),
            r("What attitude does the team take towards its findings?", 0, "It recognises the survey's limits and recommends review.", "Cautious and willing to revise", "Certain that every season is identical", "Unwilling to consider residents", "Committed to building immediately")),
        new EnglishExercise("hcmus-transformations-practice-v1", WRITING, "Sentence transformation: meaning and structure", "HCMUS original preparation · 5 transformations", 12, 0,
            "Rewrite each sentence using the opening given, keeping the meaning. 1. The office will send the results tomorrow. → The results ... 2. I started working here three years ago and still work here. → I have ... 3. The box is too heavy for me to carry. → The box is not ... 4. 'I am waiting for the bus now,' Mai said. → Mai said that ... 5. We did not attend because we had not received the invitation. → If we ... Original practice, not recalled questions; equivalent correct answers are possible.",
            "", "", "", List.of(), List.of("Keep the tense, time and intended meaning.", "Check passive, duration, too/enough, reported speech and unreal past conditions.", "Number your five responses; review with the model after submission.")),
        new EnglishExercise("hcmus-speaking-routine-v1", SPEAKING, "Introduction and follow-up: study routines", "HCMUS original preparation · Introduction / guided conversation", 10, 0,
            "Introduce yourself using true details about where you live, your work or study and your reason for postgraduate study. Then answer: How do you organise a busy week? Where do you prefer to study, and why? Describe a time you changed a plan. What would you like to improve about your English practice? These are original practice topics, not a forecast or confirmed institution question bank.",
            "", "", "", List.of(), List.of("Use your own details, not the model speaker's biography.", "Develop a direct answer with a reason and a small example.", "Practise follow-up questions, not a memorised essay."))
    );
    private static final Map<String, EnglishReferenceResponse> MODELS = Map.of(
        "hcmus-transformations-practice-v1", model("Possible transformations with explanations", """
            1. The results will be sent by the office tomorrow.
            2. I have worked here for three years. / I have been working here for three years.
            3. The box is not light enough for me to carry.
            4. Mai said that she was waiting for the bus then.
            5. If we had received the invitation, we would have attended.

            Notice that changing the structure must not change the time or the cause. The first answer keeps tomorrow; the fifth describes an unreal past situation, not a future plan.
            """, note("Mẫu giữ nguyên ý và mốc thời gian; câu 2 có hai cách tự nhiên khi công việc vẫn tiếp diễn. Không chấm bằng khớp chuỗi tuyệt đối.", "The models preserve meaning and time; item 2 allows two natural forms for continuing work. Exact string matching is not used."),
            note("Câu 4 giả định tường thuật ở thời điểm sau nên lùi thì và đổi now thành then; trong hội thoại ngay cùng thời điểm có thể có cách diễn đạt khác.", "Item 4 assumes later reporting, hence backshift and now→then; immediate same-time reporting may permit a different wording.")),
        "hcmus-speaking-routine-v1", model("An original introduction and follow-up example", """
            Introduction: My name is Linh. I live in Ho Chi Minh City and work in a small technical team. I would like to study for a master's degree because I want to understand the ideas behind the systems I use, rather than only following instructions. I am especially interested in learning how to evaluate evidence and explain my decisions clearly.

            Organising a busy week: I check my work commitments first, then choose a few realistic study periods. On busy days I study for twenty minutes rather than cancelling everything. For example, I can review a short grammar exercise after dinner and write down one mistake to revisit.

            A place to study: I prefer a quiet desk at home for reading because I can keep my notes together. For a group discussion, a bookable library room is better. The best place depends on the activity, not simply on whether it is always silent.

            Changing a plan: Last month I planned to study with a friend on Saturday, but I had to work. I contacted my friend early and suggested a short online meeting on Sunday. We reduced the number of exercises so that we could still discuss them carefully.

            Improving English practice: I want to rely less on recognising familiar words. I will listen to a short recording without text first, then use its transcript to check what I missed. After that I can hide the text and listen again. This gives me a useful next step instead of just a score.
            """, note("Mẫu minh hoạ trả lời trực tiếp, có lý do và ví dụ; tên, công việc và mục tiêu là hư cấu. Dùng thông tin thật của bạn.", "The example uses direct answers, reasons and examples; its name, job and goals are fictional. Use your own true details."),
            note("Không cần học thuộc một bài dài. Tập nghe câu hỏi, trả lời đúng trọng tâm rồi phát triển thêm khi người hỏi yêu cầu.", "Do not memorise a long script. Listen to the question, answer its point and develop it when asked."))
    );
    static Optional<EnglishReferenceResponse> reference(String id) { return Optional.ofNullable(MODELS.get(id)); }
}
