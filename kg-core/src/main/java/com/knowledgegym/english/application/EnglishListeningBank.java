package com.knowledgegym.english.application;

import com.knowledgegym.english.domain.model.EnglishExercise;
import com.knowledgegym.english.domain.model.EnglishExercise.Item;
import com.knowledgegym.english.domain.model.EnglishExercisePart;
import java.util.ArrayList;
import java.util.List;
import static com.knowledgegym.english.domain.model.EnglishExercise.Skill.LISTENING;

/** Original recordings: eight notices, three conversations and three talks, 35 MCQs. */
final class EnglishListeningBank {
    private EnglishListeningBank() {}
    private static Item q(String id, String stem, int answer, String why, String... options) {
        return new Item(id, stem, List.of(options), answer, why);
    }
    private static EnglishExercise clip(String id, String title, String focus, int minutes, String audio, String transcript, Item... items) {
        return new EnglishExercise(id, LISTENING, title, focus, minutes, 0,
            "Listen to the original practice recording and choose the best answer based on what is stated or implied. Replay and speed controls are learning aids, not conditions of an official examination.",
            "", audio, transcript.strip(), List.of(items), List.of("Read the questions before listening.", "Separate suggestions from final decisions.", "Listen for purpose, detail, attitude and implied meaning."));
    }
    private static final EnglishExercise NOTICES = clip("listening-eight-notices-v1", "Eight everyday announcements", "Part 1 practice · 8 notices / 8 questions", 10,
        "/english/audio/notices-campus-v1.mp3", """
        Announcement one. The north entrance to the sports centre will be closed tomorrow while the steps are repaired. Please use the side entrance beside the bicycle racks. The swimming pool and all exercise classes will run at their usual times. Staff will be outside to direct visitors.

        Announcement two. Your appointment with the careers adviser is confirmed for Wednesday at ten thirty. Please bring a printed copy of your current CV. You do not need to prepare a presentation. If you cannot attend, contact the office before Tuesday afternoon so that the appointment can be offered to another student.

        Announcement three. The train to the coast will leave from platform six, not platform three as shown on some tickets. Its departure time remains twelve fifteen. Passengers already waiting on platform three should follow the signs to the footbridge. There is no change to the return service this evening.

        Announcement four. Thank you for calling the bookshop. The title you ordered has arrived, and we can hold it until Friday. Our shop closes at six on weekdays, but on Thursday we remain open until eight. If collecting the book is difficult, we can arrange delivery for an additional charge.

        Announcement five. Before starting the workshop, please place your bags under the tables and put on the safety glasses provided. Keep your glasses on whenever tools are being used, even if you are only watching. There will be a short break halfway through the session, when refreshments will be available outside.

        Announcement six. The walking tour will begin beside the main library at two o'clock. Heavy rain is expected later in the afternoon, so we have shortened the route and will finish at the museum by three. Please bring a waterproof jacket. The museum visit is included in your ticket.

        Announcement seven. Residents can bring unwanted electrical items to the community hall on Saturday morning. The collection is for small appliances only; we cannot accept refrigerators or washing machines. Please remove any batteries before handing an item to a volunteer. There is no charge for using this service.

        Announcement eight. We are moving the evening language class to room twenty-four because the usual classroom is being painted. The course still begins at six thirty. Please use the stairs near reception, as the lift will be unavailable tonight. Anyone who needs step-free access should call the office so that we can arrange an alternative.
        """,
        q("nt1", "Why must visitors use a different entrance?", 1, "The north entrance steps are being repaired; activities continue normally.", "The swimming pool is closed", "The entrance steps need repair", "All exercise classes have moved", "The bicycle racks are being removed"),
        q("nt2", "What should the student bring to the appointment?", 2, "The message requests a printed CV and says a presentation is unnecessary.", "A prepared presentation", "An examination timetable", "A printed CV", "A payment receipt"),
        q("nt3", "What has changed for the train journey?", 0, "The platform changes from three to six, but the time remains twelve fifteen.", "The departure platform", "The departure time", "The destination", "The return service"),
        q("nt4", "On which weekday can the customer collect the book after six?", 3, "The shop remains open until eight on Thursday.", "Monday", "Wednesday", "Friday", "Thursday"),
        q("nt5", "Who must wear safety glasses while tools are being used?", 1, "The instructions include people who are only watching.", "Only the instructor", "Everyone, including observers", "Only people using a tool", "People serving refreshments"),
        q("nt6", "What have the organisers done because of the forecast?", 2, "They shortened the route and will finish at the museum by three.", "Cancelled the entire visit", "Changed the starting time", "Shortened the walking route", "Removed the museum from the ticket"),
        q("nt7", "What can residents hand in?", 0, "Only small electrical appliances are accepted, after batteries are removed.", "A small appliance without its batteries", "A refrigerator", "A washing machine", "Any appliance with batteries still inside"),
        q("nt8", "What should a person needing step-free access do?", 3, "The announcement asks them to call the office to arrange an alternative.", "Wait for the lift to reopen at six thirty", "Go directly to the usual classroom", "Use the stairs near reception", "Contact the office for an alternative"));
    private static final EnglishExercise STUDY = clip("listening-study-space-v1", "Choosing a study space", "Part 2 practice · Conversation / 4 questions", 8,
        "/english/audio/dialogue-study-space-v1.mp3", """
        Anna: Have you decided where our group should meet to prepare the presentation? The cafe near the station has large tables, and it's easy for everyone to reach.
        Ben: I thought about it, but when I went there yesterday the music was quite loud. We need to record part of the presentation, so I'm not sure it would work.
        Anna: That's a good point. We could use the library. I usually study on the second floor because it's quiet.
        Ben: It is quiet, but that area is for individual work. The librarian asked a group to stop talking when I was there last week. There are bookable discussion rooms on the ground floor, though.
        Anna: I didn't know that. Do we have to pay for one?
        Ben: No, students can book them free of charge for up to two hours. We would need to reserve a room online, and one person has to bring a student card to collect the key.
        Anna: Two hours should be enough if we arrive with our notes ready. What about Tuesday afternoon? I finish my class at three.
        Ben: I have a laboratory session then. Could we meet on Wednesday morning instead? I'm free from ten until twelve.
        Anna: Wednesday works for me, but we should check with the other two members before making a booking.
        Ben: I'll send them a message now. If they agree, I can reserve a room this evening.
        Anna: Great. I'll make a shared document so everyone can add their main points before the meeting. That should save us time.
        Ben: And let's decide what we actually need to record. We shouldn't spend the whole session choosing a topic that we've already discussed.
        Anna: Agreed. I'll put a short agenda in the document as well. If we can't find an available room, we can ask the department whether there is an empty classroom, rather than going back to the noisy cafe.
        """,
        q("st1", "Why is the cafe unsuitable?", 2, "Ben says loud music would make recording difficult.", "The tables are too small", "It is far from the station", "The music would interfere with recording", "Students must pay to reserve a table"),
        q("st2", "What is different about the library's ground-floor rooms?", 0, "They are discussion rooms that students can book, unlike the individual study area.", "They can be booked for group discussions", "They are reserved for laboratory work", "They remain open without time limits", "They require payment from students"),
        q("st3", "What must happen before Ben reserves a room?", 3, "Anna asks him to check that the other two members can attend.", "Anna must finish recording the presentation", "The cafe must change its music", "Their department must buy a key", "The other group members must confirm the time"),
        q("st4", "What can be inferred about the shared document?", 1, "Adding points and an agenda beforehand is intended to make the limited meeting time useful.", "It will replace every group meeting", "It should help the group use meeting time efficiently", "It is needed to pay for the room", "It contains a completed presentation already"));
    private static final EnglishExercise VOLUNTEERS = clip("listening-volunteer-shifts-v1", "A useful first volunteering role", "Part 2 practice · Conversation / 4 questions", 8,
        "/english/audio/dialogue-volunteer-shifts-v1.mp3", """
        Anna: I'm interested in volunteering at the community centre, but I can only come on Saturday mornings. Does that rule out most of the opportunities?
        Ben: Not at all. We have a reading group and a repair event on Saturdays. We also need people at the welcome desk to explain where activities are taking place.
        Anna: I like reading, although I've never led a group before. Would I have to plan the whole session myself?
        Ben: No. New volunteers work with an experienced organiser first. You could help choose short texts and talk with participants while the organiser leads the main discussion.
        Anna: That sounds manageable. I was also curious about the repair event. I'm not very good with tools, though.
        Ben: Technical work is done by volunteers who have the relevant skills. Other people register visitors, label items and keep the waiting area organised. You wouldn't be asked to repair something you didn't understand.
        Anna: I think I'd prefer the reading group to start with. I enjoy talking about stories, and I could learn how the sessions work before taking on more responsibility.
        Ben: That's sensible. We ask new volunteers to attend a short introduction before their first session. It covers safety, privacy and what to do if someone asks for help you cannot provide.
        Anna: Is that also held on Saturday?
        Ben: The next introduction is on Friday evening, but there is an online option if you can't attend. You still need to complete it before working with the group.
        Anna: I can probably manage Friday. Do you need a commitment for the whole year?
        Ben: We usually agree on a six-week trial. That gives you time to see whether the role suits you, and it helps us plan the rota. If you need to miss a morning, tell the organiser in advance.
        Anna: I appreciate that. I want to be useful, but I don't want to promise more time than I can actually give.
        Ben: A reliable small commitment is more helpful than an ambitious promise that someone cannot keep. I'll send you the introduction details and the reading group's contact information.
        """,
        q("vs1", "What initially concerns Anna?", 1, "She worries that being available only on Saturday mornings may limit opportunities.", "She has to purchase tools", "Her limited availability might prevent volunteering", "She must lead every activity alone", "The centre has no reading group"),
        q("vs2", "How are inexperienced volunteers supported in the reading group?", 3, "They work with an experienced organiser who leads the main discussion.", "They must plan all sessions immediately", "They can ignore preparation", "They are restricted to the welcome desk", "They begin alongside an experienced organiser"),
        q("vs3", "What is required before Anna starts?", 0, "She must complete the introduction, in person or online.", "Completing an introductory session", "Agreeing to volunteer for a whole year", "Buying repair equipment", "Leading a discussion independently"),
        q("vs4", "What is Ben's attitude towards time commitments?", 2, "He explicitly prefers a reliable small commitment over an unrealistic ambitious promise.", "Only a large commitment is worthwhile", "Missed mornings never matter", "A realistic reliable commitment is valuable", "Volunteers should avoid agreeing a rota"));
    private static final EnglishExercise HEAT = clip("listening-urban-shade-v1", "Shade, streets and local evidence", "Part 3 practice · Talk / 5 questions", 10,
        "/english/audio/talk-urban-shade-v1.mp3", """
        Today I'd like to discuss why shade matters in towns and how a community can decide where it is needed. When people talk about hot streets, they sometimes assume that the solution is simply to plant as many trees as possible. Trees can provide important benefits, but a useful plan needs to begin with the places people use and the conditions those places create.

        Imagine two routes to the same bus stop. One is slightly shorter but crosses a wide area of exposed paving. The other passes buildings and trees that provide shade. On a hot afternoon, the longer route may be more comfortable, especially for someone who walks slowly or needs to rest. Looking only at distance would miss that difference. A practical assessment should consider when people travel, where they wait and whether they have somewhere to pause safely.

        Residents can help identify these problems. They may know that a bus shelter becomes uncomfortable in the late afternoon or that children queue outside a school gate without cover. Their observations can reveal needs that a map alone does not show. However, a single account should not be treated as a complete description of every season. Visiting the site at different times and combining observations with measurements gives planners a stronger basis for a decision.

        Planting also requires attention to local conditions. A tree needs suitable space for roots, access to water while it establishes itself and a realistic maintenance plan. Selecting an attractive species without considering those needs can lead to repeated failure. In some places, a carefully designed shelter may provide a useful short-term improvement while trees mature. These options should be considered together rather than presented as competing solutions that cannot coexist.

        Fairness is another concern. Streets that already look attractive may receive more attention because improvements are easier to display there. Yet an exposed route used by people travelling to a clinic may have a stronger need. Planning should ask who benefits and who is still left without protection. This does not mean ignoring pleasant places. It means making the reasons for investment clear instead of assuming that the most visible location is always the most important.

        Finally, a project should be reviewed after it is introduced. Counting trees planted is not the same as knowing whether people can comfortably reach everyday services. Some trees may fail, a shelter may face the wrong direction, or a seating area may be difficult to reach. Feedback and follow-up visits can identify adjustments. The main lesson is that shade is part of how a street functions for people. A successful plan combines local knowledge, suitable design and ongoing care, rather than relying on a planting total alone.
        """,
        q("hs1", "What is the speaker's main point?", 2, "The talk argues that useful shade planning requires understanding use, conditions, design and maintenance.", "The shortest walking route is always best", "Shelters should replace every tree", "Shade planning needs to consider people and local conditions", "Counting trees is enough to evaluate success"),
        q("hs2", "Why are two routes to a bus stop compared?", 0, "The comparison shows that comfort and shade can make a longer route preferable.", "To show that distance alone does not determine a route's usefulness", "To demonstrate that buses should be removed", "To recommend building only wide paved routes", "To explain why all residents walk at the same speed"),
        q("hs3", "How should residents' observations be used?", 3, "The speaker recommends combining observations over different times with measurements.", "As a complete account of every season", "Only after all planting is finished", "Instead of any site visits", "Alongside further observation and measurements"),
        q("hs4", "What does the speaker imply about a shelter and trees?", 1, "A shelter can offer a short-term benefit while trees mature, so the options can coexist.", "They can never be used in the same plan", "They can serve complementary purposes", "Trees require no maintenance if a shelter exists", "Shelters are useful only in attractive streets"),
        q("hs5", "Which outcome would best indicate the practical success of the project?", 2, "The conclusion emphasises comfortable access to everyday services, not planting numbers alone.", "A larger number of planning posters", "More attention to already attractive streets", "People being better able to reach services comfortably", "A higher tree count without follow-up visits"));
    private static final EnglishExercise LEARNING = clip("listening-retrieval-practice-v1", "When remembering feels difficult", "Part 3 practice · Talk / 5 questions", 10,
        "/english/audio/talk-retrieval-practice-v1.mp3", """
        Many learners decide whether a study session was successful by asking how familiar the material feels at the end. If a page is easy to read for a second time, they may assume that they will remember it later. Familiarity can be useful, but it is not the same as being able to produce an answer without the page in front of you. Today I want to explain how a small change in study habits can make that distinction visible.

        After reading a short section, a learner can close the book and write down the main idea or answer a question from memory. This is often called retrieval practice. The attempt may feel more difficult than rereading, and the learner may discover gaps that were not obvious before. That difficulty is not automatically a sign that the method has failed. It provides information about what is available in memory and what needs further attention.

        Feedback is essential. If the learner cannot remember an answer, simply repeating the same guess is unlikely to help. Checking a reliable explanation allows the learner to correct an error and understand why it occurred. A useful follow-up is to attempt the question again later, without keeping the answer visible. Typing a sentence immediately after copying it may produce an accurate response, but it does not by itself demonstrate independent recall.

        The spacing of attempts matters too. Completing many identical questions in one sitting can create a feeling of speed and confidence because the answer remains active in the mind. Returning to a topic after some time asks the learner to reconstruct it. The appropriate interval depends on the material and the learner's needs; there is no single schedule that is ideal for everyone. The important principle is to revisit knowledge rather than assume that one successful attempt makes it permanent.

        This approach should not turn learning into an endless sequence of tests. Retrieval works best as one part of a broader process that includes explanation, discussion and using ideas in meaningful situations. A student learning a phrase, for example, can practise recalling its meaning and then use it to explain a real preference. Correct spelling alone does not show that the phrase will fit every context. Learning also involves understanding when and why an expression is appropriate.

        Finally, records of practice need careful interpretation. A correct answer after a hint can be useful progress, but it should be distinguished from an unaided answer. A mistake can identify a productive next step rather than justify a punishment. The goal is not to collect a perfect-looking score. It is to make learning more visible so that time can be spent on the ideas that still need work. A good study routine helps the learner become both more capable and more realistic about what they know.
        """,
        q("rp1", "What distinction does the speaker emphasise?", 1, "The introduction contrasts familiarity while rereading with producing an answer independently.", "Fast reading versus expensive materials", "Familiarity versus independent recall", "Writing versus speaking ability", "Short books versus long books"),
        q("rp2", "How does the speaker interpret difficulty during retrieval?", 3, "Difficulty can reveal gaps and does not automatically show failure.", "As proof that rereading is always better", "As a reason to stop studying permanently", "As evidence that feedback is unnecessary", "As potentially useful information about memory gaps"),
        q("rp3", "What should follow checking an explanation?", 0, "The speaker recommends attempting the question later without the visible answer.", "A later attempt without the answer in view", "Repeated copying as the only activity", "Avoiding the topic from then on", "Treating the next answer as a certified score"),
        q("rp4", "What does the speaker say about study intervals?", 2, "The interval depends on the material and learner; no single ideal schedule applies to everyone.", "Every learner needs exactly the same interval", "Spacing removes the need to understand ideas", "Intervals should suit the material and learner", "All questions must be repeated in one sitting"),
        q("rp5", "Which statement best reflects the speaker's attitude?", 1, "The conclusion values realistic understanding and useful next steps rather than perfect-looking scores.", "Mistakes should always be punished", "Practice should inform the next step, not merely produce impressive scores", "A hint makes learning worthless", "One correct answer proves permanent knowledge"));

    static List<EnglishExercise> exercises(List<EnglishExercise> released) {
        var dialogue = released.stream().filter(e -> e.id().equals("listening-dialogue-v1")).findFirst().orElseThrow();
        var talk = released.stream().filter(e -> e.id().equals("listening-talk-v1")).findFirst().orElseThrow();
        var revisedDialogue = revise(dialogue, "listening-community-event-v2", "Part 2 practice · Conversation / 4 questions", "ev",
            q("ev4", "What will Anna do that evening?", 3, "Anna says she will write the shop notice that night; Ben calls the manager the following morning.", "Call the hall manager", "Borrow chairs from the school", "Cancel the event", "Write the notice"));
        var revisedTalk = revise(talk, "listening-small-habits-v2", "Part 3 practice · Talk / 5 questions", "hb",
            q("hb4", "What role do larger goals play?", 0, "The speaker says larger goals provide direction while daily actions provide consistency.", "They provide direction", "They replace daily actions", "They remove the need for cues", "They should be used as punishment"),
            q("hb5", "How does the speaker view an occasional missed day?", 2, "The speaker recommends restarting instead of punishment and says habits are not tests of perfection.", "As proof that a routine is impossible", "As a reason to double every later target", "As a setback that can be followed by restarting", "As evidence that reading should be abandoned"));
        var clips = List.of(NOTICES, revisedDialogue, STUDY, VOLUNTEERS, revisedTalk, HEAT, LEARNING);
        var complete = new EnglishExercise("listening-complete-practice-v1", LISTENING, "Listening: three parts, thirty-five questions",
            "Complete skill-format practice · 8 + 12 + 15 questions", 40, 0,
            "Practise eight short notices, three conversations with four questions each, and three talks with five questions each. Suggested total time: approximately 40 minutes. This is original VSTEP-format practice, not an official paper. Learning mode permits replay and speed changes; an official listening test plays the recording once.",
            "", "/english/audio/listening-complete-practice-v1.mp3",
            String.join("\n\n", clips.stream().map(e -> e.title() + "\n" + e.transcript()).toList()),
            clips.stream().flatMap(e -> e.items().stream()).toList(),
            List.of("Part 1: eight notices, one question per notice.", "Part 2: three conversations, four questions per conversation.", "Part 3: three talks, five questions per talk.", "Replay controls are for practice, not official exam conditions."));
        var all = new ArrayList<>(clips); all.add(complete); return List.copyOf(all);
    }
    private static EnglishExercise revise(EnglishExercise original, String id, String focus, String prefix, Item... extra) {
        var items = new ArrayList<Item>();
        for (int i = 0; i < original.items().size(); i++) {
            var q = original.items().get(i);
            items.add(new Item(prefix + (i + 1), q.stem(), q.options(), q.correctIndex(), q.explanation()));
        }
        items.addAll(List.of(extra));
        return new EnglishExercise(id, LISTENING, original.title() + " (expanded practice)", focus, original.minutes(), 0,
            original.prompt(), "", original.audioPath(), original.transcript(), List.copyOf(items), original.checklist());
    }
    static List<EnglishExercisePart> parts(String id, List<EnglishExercise> exercises) {
        if (!id.equals("listening-complete-practice-v1")) return List.of();
        return exercises.stream().filter(e -> !e.id().equals(id)).map(e -> new EnglishExercisePart(e.id(), e.focus() + ": " + e.title(), "", e.audioPath(), e.items().stream().map(Item::id).toList())).toList();
    }
}
