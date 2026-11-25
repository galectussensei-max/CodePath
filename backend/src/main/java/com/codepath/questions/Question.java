package com.codepath.questions;

import java.util.ArrayList;
import java.util.List;

import com.codepath.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "questions", uniqueConstraints = @UniqueConstraint(name = "uk_questions_slug", columnNames = "slug"))
@Getter
@Setter
@NoArgsConstructor
public class Question extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "concept_id", nullable = false)
    private Concept concept;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "slug", nullable = false, length = 200)
    private String slug;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 20)
    private Difficulty difficulty;

    @Column(name = "placement_tags", columnDefinition = "text")
    private String placementTags;

    @Column(name = "starter_code_java", columnDefinition = "text")
    private String starterCodeJava;

    @Column(name = "constraints", columnDefinition = "text")
    private String constraints;

    @Column(name = "time_limit_ms", nullable = false)
    private Integer timeLimitMs;

    @Column(name = "memory_limit_kb", nullable = false)
    private Integer memoryLimitKb;

    @OneToMany(mappedBy = "question")
    private List<TestCase> testCases = new ArrayList<>();
}
