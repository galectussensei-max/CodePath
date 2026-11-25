package com.codepath.questions;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByQuestionIdAndHiddenFalse(Long questionId);

    List<TestCase> findByQuestionId(Long questionId);
}
