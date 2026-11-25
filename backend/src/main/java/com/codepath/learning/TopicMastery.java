package com.codepath.learning;

import java.time.Instant;

import com.codepath.common.BaseEntity;
import com.codepath.questions.Topic;
import com.codepath.users.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Heuristic estimate of a learner's topic mastery on a 0.0 to 100.0 scale.
 * It is a transparent rule-based estimate, not a measurement of true ability.
 */
@Entity
@Table(
        name = "topic_mastery",
        uniqueConstraints = @UniqueConstraint(name = "uk_topic_mastery_user_topic", columnNames = {"user_id", "topic_id"}))
@Getter
@Setter
@NoArgsConstructor
public class TopicMastery extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "mastery_score", nullable = false)
    private double masteryScore = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MasteryStatus status = MasteryStatus.UNASSESSED;

    @Column(name = "attempts_count", nullable = false)
    private int attemptsCount;

    @Column(name = "solved_count", nullable = false)
    private int solvedCount;

    @Column(name = "last_practiced_at")
    private Instant lastPracticedAt;

    @Column(name = "next_revision_due")
    private Instant nextRevisionDue;
}
