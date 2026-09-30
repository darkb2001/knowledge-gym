package com.knowledgegym.content.domain.model;

import java.util.UUID;

/**
 * Lựa chọn trả lời của câu hỏi (bảng `question_options`).
 *
 * Lưu ý bảo mật: `correct` KHÔNG bao giờ được trả ra API public — chỉ admin DTO mới thấy.
 * m4 chưa sinh option nào (để m6 quiz sinh distractor), nhưng model đã sẵn.
 */
public class QuestionOption {

    private UUID id;
    private UUID questionId;
    private String content;
    private boolean correct;
    private int displayOrder;

    public QuestionOption(String content, boolean correct, int displayOrder) {
        this.content = content;
        this.correct = correct;
        this.displayOrder = displayOrder;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getQuestionId() { return questionId; }
    public void setQuestionId(UUID questionId) { this.questionId = questionId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
}
