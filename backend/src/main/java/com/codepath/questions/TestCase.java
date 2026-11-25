package com.codepath.questions;

import com.codepath.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "test_cases")
@Getter
@Setter
@NoArgsConstructor
public class TestCase extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "input_data", nullable = false, columnDefinition = "text")
    private String inputData;

    @Column(name = "expected_output", nullable = false, columnDefinition = "text")
    private String expectedOutput;

    @Column(name = "is_hidden", nullable = false)
    private boolean hidden;
}
