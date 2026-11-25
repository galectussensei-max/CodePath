package com.codepath.submissions;

import java.time.Instant;

import com.codepath.common.BaseEntity;
import com.codepath.questions.Question;
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
        name = "submissions",
        indexes = {
                @Index(name = "idx_submissions_user_question", columnList = "user_id, question_id"),
                @Index(name = "idx_submissions_created_at", columnList = "created_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class Submission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "source_code", nullable = false, columnDefinition = "text")
    private String sourceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 20)
    private Language language = Language.JAVA;

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", nullable = false, length = 30)
    private Verdict verdict = Verdict.PENDING;

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    @Column(name = "memory_used_kb")
    private Integer memoryUsedKb;

    @Column(name = "time_taken_seconds")
    private Integer timeTakenSeconds;

    @Column(name = "hints_used_count", nullable = false)
    private int hintsUsedCount;

    @Column(name = "judge_output_log", columnDefinition = "text")
    private String judgeOutputLog;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (language == null) {
            language = Language.JAVA;
        }
        if (verdict == null) {
            verdict = Verdict.PENDING;
        }
    }
}
