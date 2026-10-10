package com.knowledgegym.english.application;

import com.knowledgegym.english.domain.model.EnglishExercise;
import com.knowledgegym.english.domain.model.EnglishReferenceResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static com.knowledgegym.english.application.EnglishReferenceResponses.model;
import static com.knowledgegym.english.application.EnglishReferenceResponses.note;
import static com.knowledgegym.english.domain.model.EnglishExercise.Skill.*;

/** Fresh original variants on study/work/family themes, not alleged recalled exam questions. */
final class EnglishCurrentThemeBank {
    private EnglishCurrentThemeBank() {}
    static final List<EnglishExercise> EXERCISES = List.of(
        new EnglishExercise("writing-student-work-v1", WRITING, "Work alongside study", "Task 2 · Advantages and disadvantages", 40, 250,
            "University students sometimes take part-time work during their studies. Discuss the advantages and disadvantages of combining employment with a degree, and give your view on how students can make a responsible choice. Support your ideas with reasons and examples. Write at least 250 words.", "", "", "", List.of(),
            List.of("Discuss both advantages and disadvantages.", "Develop each point rather than listing it.", "Give a clear view that follows from your discussion.", "Check organisation, vocabulary and grammar.")),
        new EnglishExercise("speaking-family-work-v1", SPEAKING, "Family and working life", "Part 1 · Social interaction", 3, 0,
            "Answer aloud. Topic 1: Family. How do you usually spend time with your family? Who has taught you a useful skill? Has the way your family communicates changed? Topic 2: Working life. What kind of work interests you? Would you prefer to work alone or in a team? What skill would you like to develop for your future work?", "", "", "", List.of(),
            List.of("Answer all six questions across the two topics.", "Add a reason or a specific example.", "Use your own experiences instead of memorising the model.")),
        new EnglishExercise("speaking-career-support-v1", SPEAKING, "Helping a friend plan a career", "Part 2 · Solution discussion", 4, 0,
            "A friend is unsure which career to pursue and wants an affordable way to understand different kinds of work. The options are attending a large careers fair, arranging a short job-shadowing visit, or taking a general online personality quiz. Choose the best option, support it with reasons, and compare the other two options.", "", "", "", List.of(),
            List.of("Relate your choice to the friend's needs and budget.", "Explain why the other two options are less suitable.", "Recognise a limitation of your choice without abandoning your argument."))
    );
    private static final Map<String, EnglishReferenceResponse> MODELS = Map.of(
        "writing-student-work-v1", model("A considered approach to part-time work", """
            Combining a degree with part-time work can offer students valuable opportunities, but it can also place pressure on their studies and personal lives. The benefits depend on the nature of the job and the time available. I believe that students should consider employment carefully rather than assume that it is either always desirable or always harmful.

            One advantage is financial independence. Earning some income can help a student cover everyday expenses and make informed decisions about spending. Work may also develop practical skills that are difficult to learn from a textbook. For example, a student serving customers can practise listening, explaining information and resolving misunderstandings. These experiences may build confidence and provide examples to discuss in a future job interview.

            However, employment can compete with academic responsibilities. A shift that ends late may leave little time to prepare for the next day's class, and an unpredictable schedule can make group projects difficult to organise. The problem becomes more serious when students accept extra hours because they feel unable to refuse. Even a job that teaches useful skills can be a poor choice if it regularly prevents a student from completing essential coursework or resting properly.

            Another consideration is whether the work offers suitable conditions. A convenient location and a manager who respects agreed hours may matter more than a slightly higher wage. Students should check their responsibilities before accepting a role and avoid relying on informal promises about flexibility. Universities can help by offering guidance on managing commitments and identifying sources of financial support, particularly when students feel forced to take on excessive work.

            In conclusion, part-time employment can provide income and useful experience, but its demands must be balanced against the purpose of studying. A manageable role with predictable hours is more likely to be beneficial than a job chosen solely for immediate earnings. Students should review the arrangement when their course workload changes rather than continue automatically.
            """, note("Bài xét lợi ích và bất lợi rồi đưa ra quan điểm có điều kiện. Ví dụ gắn với việc làm thật, không dùng số liệu dự đoán hay khẳng định tất cả sinh viên giống nhau.", "The response considers both sides and reaches a qualified position. Examples concern realistic work situations, without predictions or claims that all students are alike.")),
        "speaking-family-work-v1", model("Social interaction: family and work", """
            How do you usually spend time with your family?
            We often have a meal together at the weekend. It gives us a chance to talk without rushing, and we sometimes cook something that everyone can help prepare. We don't need an expensive activity to enjoy spending time together.

            Who has taught you a useful skill?
            My aunt taught me how to plan a simple budget. She showed me how to separate essential expenses from things I could wait to buy. I still use that approach when I have to make a larger purchase.

            Has the way your family communicates changed?
            Yes. We use a group chat more often now because some relatives live further away. It's convenient for sharing news, although I still prefer a proper conversation when something important needs discussing.

            What kind of work interests you?
            I'm interested in work that combines problem-solving with helping people. For example, I would enjoy explaining how a service works and finding a practical way to improve it. I'd like to gain some experience before choosing a particular role.

            Would you prefer to work alone or in a team?
            I like having time to concentrate alone, but I also value discussing ideas with other people. For a complex task, a small team can notice problems that one person might miss. Clear responsibilities help make that teamwork useful.

            What skill would you like to develop for your future work?
            I'd like to become more confident at giving presentations. I can usually explain an idea to one person, but a larger audience makes me nervous. Short opportunities to practise and receive feedback would help.
            """, note("Hai chủ đề và sáu câu hỏi bám dạng tương tác xã hội. Bạn có thể chọn ví dụ khác; không cần cố dùng mọi từ khó trong bài mẫu.", "Two topics and six questions follow social interaction. Choose different examples if needed; there is no need to force every advanced expression from the model.")),
        "speaking-career-support-v1", model("Solution discussion: seeing a job in practice", """
            I would choose a short job-shadowing visit because my friend needs to understand what different kinds of work actually involve. Spending some time with someone in a role would give them an opportunity to observe daily tasks and ask specific questions.

            This could be affordable if the visit were arranged through a university, a community contact or an employer's existing programme. My friend could prepare questions about the work, the skills required and the parts that are most challenging. However, we would need to check that the organisation is willing to host a visitor and that the arrangement respects safety and privacy.

            A large careers fair would offer a wider range of options in one place, which is an advantage. Nevertheless, conversations may be brief and focused on recruitment information. My friend might collect many leaflets without gaining a clear picture of everyday work. I would use a fair to identify possibilities, but not rely on it alone.

            A general online personality quiz would be easy to access, but its result should not decide a career. It may encourage reflection, yet it cannot show the conditions of a workplace or whether a particular role will suit my friend's circumstances. I would treat it as a starting point for questions rather than a reliable answer.

            Overall, job shadowing seems the most useful choice for this situation. One visit would not settle every career decision, but it could replace assumptions with a more concrete experience and help my friend decide what to explore next.
            """, note("Nêu phương án, hai nhóm lý do, hạn chế cần xử lý và so sánh cả hai lựa chọn còn lại. Không coi bài trắc nghiệm tính cách là bằng chứng chắc chắn cho nghề nghiệp.", "State the choice, develop reasons, address a limitation and compare both alternatives. A personality quiz is not treated as conclusive career evidence."))
    );
    static Optional<EnglishReferenceResponse> reference(String id) { return Optional.ofNullable(MODELS.get(id)); }
}
