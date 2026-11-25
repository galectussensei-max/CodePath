package com.codepath.users;

import com.codepath.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "learner_profiles")
@Getter
@Setter
@NoArgsConstructor
public class LearnerProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "programming_background", length = 500)
    private String programmingBackground;

    @Column(name = "dsa_experience", length = 500)
    private String dsaExperience;

    @Column(name = "prep_goal", length = 500)
    private String prepGoal;

    @Column(name = "target_level", length = 100)
    private String targetLevel;

    @Column(name = "daily_study_minutes")
    private Integer dailyStudyMinutes;

    @Column(name = "diagnostic_completed", nullable = false)
    private boolean diagnosticCompleted;
}
