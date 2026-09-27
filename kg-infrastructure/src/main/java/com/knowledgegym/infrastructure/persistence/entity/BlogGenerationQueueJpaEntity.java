package com.knowledgegym.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "blog_generation_queue")
public class BlogGenerationQueueJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "angle", columnDefinition = "TEXT")
    private String angle;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "selection_strategy", nullable = false, length = 20)
    private String selectionStrategy;

    @Column(name = "writer_strategy", nullable = false, length = 20)
    private String writerStrategy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reference_items", columnDefinition = "jsonb")
    private String referenceItems;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "scheduled_for")
    private Instant scheduledFor;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getAngle() { return angle; }
    public void setAngle(String angle) { this.angle = angle; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public String getSelectionStrategy() { return selectionStrategy; }
    public void setSelectionStrategy(String selectionStrategy) { this.selectionStrategy = selectionStrategy; }
    public String getWriterStrategy() { return writerStrategy; }
    public void setWriterStrategy(String writerStrategy) { this.writerStrategy = writerStrategy; }
    public String getReferenceItems() { return referenceItems; }
    public void setReferenceItems(String referenceItems) { this.referenceItems = referenceItems; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getScheduledFor() { return scheduledFor; }
    public void setScheduledFor(Instant scheduledFor) { this.scheduledFor = scheduledFor; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}