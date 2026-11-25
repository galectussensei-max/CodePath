package com.codepath.ai;

import java.time.Instant;

import com.codepath.common.BaseEntity;
import com.codepath.questions.Question;
import com.codepath.submissions.Submission;
import com.codepath.users.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "ai_interactions",
        indexes = @Index(name = "idx_ai_interactions_user_question", columnList = "user_id, question_id"))
@Getter
@Setter
@NoArgsConstructor
public class AiInteraction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id")
    private Submission submission;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", nullable = false, length = 30)
    private InteractionType interactionType;

    @Column(name = "prompt_context", columnDefinition = "text")
    private String promptContext;

    @Column(name = "ai_response", columnDefinition = "text")
    private String aiResponse;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
