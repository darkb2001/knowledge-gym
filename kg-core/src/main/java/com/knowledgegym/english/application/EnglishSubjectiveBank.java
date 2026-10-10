package com.knowledgegym.english.application;

import com.knowledgegym.english.domain.model.EnglishExercise;
import com.knowledgegym.english.domain.model.EnglishReferenceResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static com.knowledgegym.english.application.EnglishReferenceResponses.model;
import static com.knowledgegym.english.application.EnglishReferenceResponses.note;
import static com.knowledgegym.english.domain.model.EnglishExercise.Skill.*;

/** Original additional task variants and worked examples; no imported paper or score claim. */
final class EnglishSubjectiveBank {
    private EnglishSubjectiveBank() {}
    private static EnglishExercise writing(String id, String title, String focus, int words, String prompt) {
        return new EnglishExercise(id, WRITING, title, focus, words == 120 ? 20 : 40, words, prompt, "", "", "", List.of(),
            List.of("Cover every instruction in the task.", "Use a clear purpose or position and coherent paragraphs.", "Develop reasons with relevant details and examples.", "Check vocabulary, grammar, spelling and appropriate tone."));
    }
    private static EnglishExercise speaking(String id, String title, String focus, int minutes, String prompt) {
        return new EnglishExercise(id, SPEAKING, title, focus, minutes, 0, prompt, "", "", "", List.of(),
            List.of("Answer the actual question before adding details.", "Develop reasons with an example rather than a memorised script.", "Use clear pronunciation and a manageable natural pace.", "Review your recording; this task has no automated speech grade."));
    }
    static final List<EnglishExercise> EXERCISES = List.of(
        writing("writing-course-enquiry-v1", "Asking about an evening course", "Task 1 · Formal enquiry email", 120,
            "You have seen an advertisement for an evening English course but need more information. Write an email to the course coordinator. Explain your learning goal, ask about the timetable and class format, and ask what the fee includes. Write at least 120 words. Do not invent details as if the coordinator had already confirmed them."),
        writing("writing-transport-opinion-v1", "Investing in how people travel", "Task 2 · Opinion essay", 250,
            "Some people believe local governments should spend more money improving public transport rather than building more roads. To what extent do you agree or disagree? Give reasons and relevant examples. Write at least 250 words."),
        writing("writing-phone-habits-v1", "A routine worth changing", "Task 2 · Causes and solutions essay", 250,
            "Many students find it difficult to stop using their phones when they intend to study. What might cause this problem, and what practical measures could students and educational institutions take? Develop your ideas with reasons and examples. Write at least 250 words."),
        speaking("speaking-food-travel-v1", "Food and journeys", "Part 1 · Social interaction", 3,
            "Answer these questions aloud. Topic 1: Food. What food do you enjoy? Do you prefer cooking at home or eating out? Would you like to learn to cook a new dish? Topic 2: Travel. How do you usually travel around your town? What do you enjoy about a short trip? Where would you like to visit next?"),
        speaking("speaking-community-health-v1", "Choosing a community activity", "Part 2 · Solution discussion", 4,
            "Your neighbourhood wants an affordable activity that encourages adults with different levels of fitness to become more active. Three options are suggested: a competitive football tournament, a regular walking group, or a one-day health lecture. Choose the best option, give reasons, and explain why you did not select the other two."),
        speaking("speaking-volunteering-v1", "What volunteering can offer", "Part 3 · Topic development", 5,
            "Give a short talk about the benefits of volunteering. Suggested ideas: developing practical skills, meeting different people, and supporting a community. You may add your own ideas. Follow-up questions: Should students be required to volunteer? How can organisations support inexperienced volunteers? Can online volunteering be useful?")
    );
    private static final Map<String, EnglishReferenceResponse> MODELS = Map.of(
        "writing-course-enquiry-v1", model("An email to a course coordinator", """
            Dear Course Coordinator,

            I am writing to ask for more information about the evening English course advertised on your website. I would like to improve my speaking and writing skills because I often communicate with international customers at work. An evening course would suit me, but I need to check a few details before applying.

            Could you please tell me which evenings the classes take place and how long each session lasts? I would also like to know whether the course is taught entirely in person or includes online lessons. As my working hours sometimes change, it would be helpful to understand what happens if a student misses a class.

            Finally, could you confirm the total fee and explain whether it includes learning materials and any assessment? Please also let me know if students need to purchase additional books.

            Thank you for your assistance. I look forward to hearing from you.

            Yours faithfully,
            Minh Tran
            """, note("Nêu mục đích rõ ràng, giải thích mục tiêu học, rồi hỏi đủ lịch học, hình thức và học phí. Dùng câu hỏi lịch sự thay vì yêu cầu cụt.", "State the purpose and learning goal, then ask about the schedule, format and fee. Use polite questions rather than abrupt demands."),
            note("Đây là thư hỏi thông tin: không viết như thể học phí hay lịch đã được xác nhận. Lời chào và kết thư phù hợp với người nhận chưa biết tên.", "This is an enquiry: do not treat the schedule or fee as confirmed. The greeting and closing suit a recipient whose name is unknown.")),
        "writing-transport-opinion-v1", model("Public transport and road investment", """
            Local governments must decide how limited transport budgets can serve a growing population. Some people argue that improving public transport should take priority over building additional roads. I largely agree, although essential road maintenance and the needs of areas with few transport alternatives should not be ignored.

            The strongest reason to invest in public transport is that it can provide a practical alternative to travelling in separate cars. A reliable bus service connects residents to work, education and healthcare without requiring each household to own a vehicle. This can be especially important for people who cannot drive. For example, extending a route to a local college may help students reach classes independently, whereas an extra road lane would not directly solve their lack of access.

            Public transport improvements can also make existing streets more useful. Clear information, accessible stops and services that run at convenient times may encourage people to change how they travel. The benefits depend on the quality of the service, however. Buying new buses alone will achieve little if journeys remain unpredictable or important neighbourhoods are not connected. Investment should therefore address routes and reliability as well as equipment.

            There are still situations in which road spending is justified. Unsafe surfaces need repair, and some rural communities cannot support frequent bus services. Roads are also used by delivery vehicles and emergency services. Treating every road project as unnecessary would overlook these responsibilities. Nevertheless, building more capacity for private cars should not be the automatic response to congestion, particularly when the same budget could improve alternatives for a wider range of users.

            In conclusion, public transport should generally receive greater priority because it can improve access and offer alternatives to individual car journeys. A balanced policy would combine these improvements with necessary road maintenance, choosing projects according to local needs rather than assuming that more road space is always the best solution.
            """, note("Lập trường 'largely agree' nhất quán: ưu tiên giao thông công cộng nhưng vẫn xét bảo trì và vùng nông thôn. Không đổi ý đột ngột ở kết luận.", "The 'largely agree' position stays consistent: prioritise public transport while recognising maintenance and rural needs. The conclusion does not introduce a new position."),
            note("Mỗi đoạn có luận điểm, giải thích và chi tiết phù hợp. Tránh gán số liệu môi trường hoặc tỷ lệ tiết kiệm khi không có nguồn.", "Each paragraph develops a point with explanation and relevant detail. Avoid unsupported environmental statistics or savings percentages.")),
        "writing-phone-habits-v1", model("Managing phone use while studying", """
            Phones are useful learning tools, but many students struggle to put them aside when they need to concentrate. This problem can arise from both digital habits and the way study tasks are organised. Practical changes by students and educational institutions can help without requiring phones to disappear from daily life.

            One cause is the expectation of frequent communication. Notifications invite students to check messages, and a brief check can lead to unrelated content. A person who is waiting for an important reply may find this particularly difficult. Another cause is uncertainty about the study task itself. When an assignment feels too large or its instructions are unclear, using a phone offers an immediate activity that is easier to begin. The problem is therefore not always a simple lack of determination.

            Students can start by making the study goal specific and manageable. For example, deciding to read one section and write a short summary creates a clearer starting point than promising to study all evening. Unnecessary notifications can be turned off, and the phone can be placed somewhere that is not immediately within reach. If the device is needed for a learning activity, students can identify the relevant resources in advance rather than move between study materials and social feeds without a plan.

            Educational institutions also have a role. Teachers can provide clear instructions and help students divide a complex assignment into smaller stages. Libraries can offer quiet spaces where focused work is easier, while discussions about digital habits can encourage realistic strategies instead of blame. Strict bans may be appropriate in some situations, but they should not replace explaining how to use technology responsibly.

            In conclusion, distracting phone use can be influenced by communication habits and unclear or overwhelming tasks. Clear study goals, fewer unnecessary interruptions and supportive learning conditions offer a more useful response than expecting willpower alone to solve the problem.
            """, note("Đề yêu cầu nguyên nhân và giải pháp: bài phát triển cả hai, đồng thời nêu việc người học và cơ sở giáo dục có thể làm.", "The task asks for causes and measures: both are developed, including actions by students and institutions."),
            note("Các giải pháp nối với nguyên nhân đã nêu. Dùng 'can', 'may' và ví dụ thực tế, không chẩn đoán người học hay đưa hứa hẹn y khoa.", "Measures address the causes already discussed. Use qualified claims and practical examples, not diagnoses or medical promises.")),
        "speaking-food-travel-v1", model("Social interaction: food and travel", """
            What food do you enjoy?
            I enjoy noodle dishes, especially when they include plenty of vegetables. They're comforting and easy to share with my family. I also like trying a different version when I visit a new place.

            Do you prefer cooking at home or eating out?
            Most days I prefer cooking at home because I can choose the ingredients and keep the cost manageable. However, I enjoy eating out with friends occasionally. For me, it depends on whether I want a simple meal or a social evening.

            Would you like to learn to cook a new dish?
            Yes, I'd like to learn to make bread. I've tried it once, but the result was quite heavy. I think practising with someone who knows the technique would be more helpful than only watching videos.

            How do you usually travel around your town?
            I usually walk for short journeys and take a bus when I need to travel further. The bus is convenient on my normal route, although I allow extra time during busy periods. I don't need to drive every day.

            What do you enjoy about a short trip?
            I enjoy having a change of surroundings without making complicated plans. A short trip gives me a chance to explore a market or walk somewhere unfamiliar. I often return feeling refreshed even if I have only been away for a day.

            Where would you like to visit next?
            I'd like to visit a coastal town that I haven't seen before. I'm interested in the local food and the walking routes near the sea. I would probably go outside the busiest holiday period so that I could explore more quietly.
            """, note("Có sáu câu hỏi thuộc hai chủ đề. Mỗi câu trả lời ngắn nhưng có phát triển; hãy dùng trải nghiệm thật của bạn.", "There are six questions across two topics. Answers are brief but developed; use your own genuine experiences.")),
        "speaking-community-health-v1", model("Solution discussion: a regular walking group", """
            I'd choose a regular walking group because it is affordable and can include adults with different levels of fitness. The organisers could offer a short route and a longer option, allowing participants to join at a level that feels manageable.

            A regular activity is particularly useful here. People would not only attend once; they could build a routine and get to know neighbours who encourage them to return. The group would need a safe route and a clear meeting time, but it would not require expensive equipment. A volunteer could also check whether anyone needs a slower pace or somewhere to rest.

            A competitive football tournament might attract residents who already enjoy sport. However, it could discourage beginners or people who cannot take part in a demanding game. It would also require a suitable pitch, teams and more organisation. For a neighbourhood trying to involve a broad group at low cost, those limitations matter.

            A one-day health lecture could provide useful information, especially if there were time for questions. Nevertheless, listening to advice is not the same as becoming more active. Unless the lecture led to a practical follow-up, participants might leave without changing their routine.

            Overall, the walking group best matches the aim of this event. It combines a manageable activity with regular social support. I would review the route and participants' feedback after the first few meetings so that the group could become more accessible over time.
            """, note("Mẫu xét đúng ba tiêu chí của tình huống: chi phí, nhiều mức thể lực và khuyến khích hoạt động đều đặn. Hai phương án còn lại đều được so sánh.", "The model addresses the situation's criteria: cost, different fitness levels and regular activity. Both alternatives are compared.")),
        "speaking-volunteering-v1", model("Topic development: learning through volunteering", """
            Volunteering can benefit both a community and the people who contribute their time. I would focus on practical skills, relationships and the support that a well-organised activity can provide.

            First, volunteers can develop skills through real responsibilities. Someone helping at a community event might learn to explain instructions, organise a queue or work with other people. These skills become more useful when an experienced organiser gives feedback. Simply assigning tasks without support would not create the same learning opportunity.

            Second, volunteering can bring people into contact with others they might not normally meet. A student and a retired resident, for example, could work together at a reading group. They may discover different experiences and ideas while sharing a clear purpose. This can make a community feel more connected, although relationships need time rather than one brief meeting.

            Third, volunteers can help provide activities that matter locally, such as language practice or neighbourhood clean-ups. Their contribution should be coordinated with the needs of the people using the service. Good intentions alone are not enough; organisers must consider safety, privacy and whether the commitment is reliable.

            In conclusion, volunteering is valuable when it combines a useful contribution with learning and respectful relationships. It should support a community's real needs rather than become only a way to collect an impressive-looking achievement.

            Should students be required to volunteer?
            I would encourage participation but allow students a choice of activities and consider their other responsibilities. A requirement without flexibility could make the experience feel like a punishment rather than a useful contribution.

            How can organisations support inexperienced volunteers?
            They can give clear introductions, pair new volunteers with experienced people and provide someone to ask for help. Beginning with a manageable task is better than expecting a newcomer to know everything.

            Can online volunteering be useful?
            Yes. For example, a volunteer could help organise accessible learning materials remotely. The task still needs clear instructions and responsible handling of information, just as an in-person role does.
            """, note("Ba ý được gợi ý đều có giải thích hoặc ví dụ, rồi kết luận. Câu hỏi mở rộng được trả lời riêng, không lặp nguyên bài nói.", "All three suggested ideas have development or examples, followed by a conclusion. Follow-up answers are separate rather than repetitions of the talk."))
    );
    static Optional<EnglishReferenceResponse> reference(String id) { return Optional.ofNullable(MODELS.get(id)); }
}
