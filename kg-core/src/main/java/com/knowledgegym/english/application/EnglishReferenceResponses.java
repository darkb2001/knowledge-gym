package com.knowledgegym.english.application;

import com.knowledgegym.english.domain.model.EnglishReferenceResponse;
import com.knowledgegym.english.domain.model.EnglishReferenceResponse.Note;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Supplemental review material: released exercise prompts, keys and IDs are untouched. */
public final class EnglishReferenceResponses {
    private EnglishReferenceResponses() {}
    static EnglishReferenceResponse model(String title, String text, Note... notes) {
        return new EnglishReferenceResponse(title, text.strip(), List.of(notes));
    }
    static Note note(String vi, String en) { return new Note(vi, en); }
    private static final Map<String, EnglishReferenceResponse> RESPONSES = Map.of(
        "writing-email-v1", model("An invitation to visit", """
            Dear Alex,

            I'm really pleased that you're planning to visit my town next month. I think the second weekend would be ideal because I'll have finished my exams and will be free to spend time with you. If you arrive on Friday afternoon, I can meet you at the bus station.

            On Saturday morning, we could explore the old market together. There are several small food stalls where you can try local dishes, and I know a quiet cafe nearby. In the afternoon, I'd like to take you on a walk beside the river. It's a lovely place to relax, and you could take some photographs if the weather is clear.

            Please bring comfortable shoes because we'll be walking quite a lot. A light jacket would also be useful for the evening, and don't forget your camera. You don't need to bring any special equipment.

            Let me know whether that weekend works for you. I can't wait to see you!

            Best wishes,
            Minh
            """, note("Bài đáp ứng đủ ba yêu cầu: thời điểm đến, hai hoạt động và những thứ cần mang. Mỗi nhóm ý có đoạn riêng.", "All three points are covered: when to visit, two activities and what to bring. Each group has its own paragraph."),
            note("Đây là email cho bạn: lời chào, dạng rút gọn và lời kết thân mật phù hợp. Thay chi tiết bằng trải nghiệm thật của bạn.", "This is an email to a friend, so contractions and a friendly opening and closing fit. Adapt the details to your own experience.")),
        "writing-essay-v1", model("Online learning and the classroom", """
            Online learning has made education available to people who cannot attend regular classes. Some argue that it should therefore replace traditional teaching, while others believe that classrooms remain necessary. Although online courses offer important advantages, I believe that a combination of both approaches is more effective than replacing face-to-face education completely.

            Supporters of online learning often point to flexibility. Students can study without travelling, and recorded lessons allow them to review difficult material at a convenient time. This is particularly useful for people who work or care for family members. For example, an employee taking an evening course can watch a lesson after finishing a shift rather than miss it entirely. Online platforms can also make specialist courses available in places where suitable teachers are difficult to find.

            However, face-to-face classes provide forms of support that are harder to reproduce online. Teachers can notice confusion and adjust an explanation immediately. Students also practise communicating and cooperating when they work with classmates. In a language lesson, for instance, a short discussion gives learners an opportunity to respond naturally instead of only completing written exercises. Some subjects require practical equipment or supervised activities, and not every student has a quiet room or a reliable internet connection at home.

            In my view, institutions should choose the method according to the learners and the task. Recorded explanations and online quizzes can help students prepare before a lesson, while classroom time can be used for discussion, practical work and individual feedback. This approach preserves flexibility without assuming that technology can meet every educational need.

            In conclusion, online learning is a valuable addition to education, but it should not replace all traditional classes. A carefully planned combination can widen access while retaining the interaction and practical support that many students need.
            """, note("Mở bài nêu hai quan điểm và lập trường; hai đoạn thân bài phát triển từng phía, rồi đề xuất cách kết hợp và kết luận.", "The introduction presents both views and a position; body paragraphs develop each side, followed by a practical synthesis and conclusion."),
            note("Ví dụ đi sau lý do cụ thể. Không có số liệu hay tên nghiên cứu bịa đặt, và bài không tuyên bố một mức điểm chứng chỉ.", "Examples support specific reasons. No invented statistics or named studies are used, and no certified score is claimed.")),
        "speaking-interaction-v1", model("Social interaction: learning and neighbourhood", """
            What do you enjoy learning?
            I enjoy learning languages because they help me understand how other people think. Recently, I've been practising English through short podcasts. I like noticing an expression and then trying to use it in a conversation.

            How do you usually study?
            I usually study for a short time each evening. First, I review something I found difficult, and then I test myself without looking at my notes. That helps me see what I can actually remember.

            What would you like to learn next?
            I'd like to learn basic photography. I often take pictures when I travel, but I don't really understand lighting. A beginner's course would help me improve without buying expensive equipment immediately.

            What do you like about where you live?
            I like the fact that most everyday services are nearby. I can walk to a market and a small park, which makes my neighbourhood convenient. People also tend to recognise their neighbours, so it feels friendly.

            Is there anything you would change?
            I'd improve the pavements because some sections are narrow or uneven. That can make walking difficult, especially for older residents. Even small repairs would make a noticeable difference.

            Would you recommend it to a visitor?
            Yes, especially to someone who wants to see ordinary local life rather than only tourist attractions. There are several places to try local food. However, I'd explain the bus routes first because they can be confusing for a newcomer.
            """, note("Mỗi câu trả lời đi thẳng vào câu hỏi rồi thêm lý do hoặc ví dụ. Không biến Part 1 thành một bài diễn văn dài.", "Each answer responds directly and adds a reason or example. Part 1 should not become a long prepared speech."),
            note("Hãy đổi sở thích và nơi ở cho đúng với bạn; đọc mẫu để học cách triển khai, không học thuộc nguyên văn.", "Change the interests and neighbourhood to match your own life. Use the model to study development, not to memorise a script.")),
        "speaking-solutions-v1", model("Solution discussion: helping students make friends", """
            I'd choose the cooking workshop because new students would have a shared task and a natural reason to talk to one another. They could work in small groups to prepare a simple dish, so even a student who feels shy would have something practical to contribute.

            Another advantage is that cooking does not require everyone to have the same interests or level of fitness. Students could exchange ideas about food from their hometowns, which might help them discover things they have in common. The organisers would need to check dietary requirements and choose an affordable menu, but these are manageable problems.

            A sports afternoon could be enjoyable, and it would suit students who already like physical activities. However, some newcomers might not feel confident playing sport, especially if the event became competitive. That could leave part of the group watching instead of joining in.

            A campus tour would also be useful because new students need to find important buildings. Nevertheless, they might spend most of the time listening to a guide rather than getting to know one another. I would use the tour for orientation and organise a separate activity for making friends.

            Overall, the cooking workshop seems the best choice for this particular aim. With small groups and clear tasks, it would encourage cooperation and give students an easy way to start a conversation.
            """, note("Nêu lựa chọn ngay đầu, đưa lý do phù hợp với mục tiêu kết bạn, và giải thích cả hai phương án không chọn.", "State the choice early, support it in relation to making friends, and address both rejected options."),
            note("Đây là một lựa chọn có thể bảo vệ bằng lập luận, không phải phương án duy nhất đúng. So sánh điểm mạnh và hạn chế thay vì chỉ chê phương án khác.", "This is a defensible choice, not the only correct option. Compare strengths and limitations rather than simply criticising alternatives.")),
        "speaking-development-v1", model("Topic development: protecting the environment", """
            Individuals can protect the environment through practical choices in daily life. I would focus on transport, consumption and community activities, because these are areas where small changes can become regular habits.

            First, people can reconsider how they travel. Walking or cycling is useful for short journeys when the route is safe, while public transport may be a better option for longer trips. For example, students who live near the same bus route could travel together instead of using separate vehicles every day. Of course, these choices depend on the services available where they live.

            Second, we can buy more carefully and use what we already own. Repairing a bag or borrowing an item we need only once can reduce waste. Planning meals can also help us avoid buying food that we later throw away. The aim is not to stop all consumption, but to make it less wasteful.

            Finally, community activities can make individual efforts more effective. A neighbourhood clean-up or a shared repair event gives people a chance to learn from one another. Such activities also make environmental issues visible rather than leaving each person to act alone.

            In conclusion, individual action matters most when it is realistic and repeated. Better public services and responsible business practices should support these habits, rather than placing the whole burden on individuals.

            Which change is easiest for students?
            Planning purchases and avoiding unnecessary waste are often a practical starting point because they do not require expensive equipment. A student could begin by checking what food is already at home before shopping.

            Should governments or individuals take more responsibility?
            Governments should create the conditions for change, such as reliable transport and waste collection. Individuals still have a responsibility to use those services sensibly. The two roles support each other.

            Can technology solve environmental problems?
            Technology can help, for example by improving energy efficiency, but it cannot solve every problem on its own. People still need to decide how products are made, used and disposed of.
            """, note("Bài nói có mở ý, ba nhánh được gợi ý, ví dụ và kết luận. Phần cuối trả lời riêng từng câu hỏi mở rộng.", "The talk has an opening, three suggested branches, examples and a conclusion. Each follow-up question is answered separately."),
            note("Phân biệt bài trình bày với câu hỏi mở rộng. Khi luyện, dùng dàn ý ngắn và nghe lại bản ghi thay vì đọc toàn bộ mẫu.", "Distinguish the main talk from follow-up answers. Practise from brief notes and review your recording rather than reading the whole model."))
    );
    public static Optional<EnglishReferenceResponse> find(String exerciseId) {
        return Optional.ofNullable(RESPONSES.get(exerciseId)).or(() -> EnglishSubjectiveBank.reference(exerciseId))
            .or(() -> EnglishCurrentThemeBank.reference(exerciseId)).or(() -> EnglishHcmusBank.reference(exerciseId)).or(() -> EnglishHcmusExpansionBank.reference(exerciseId));
    }
}
