package com.codepath.questions;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codepath.common.ResourceNotFoundException;
import com.codepath.config.DataSeeder;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final TestCaseRepository testCaseRepository;

    /**
     * Problem bank listing. Diagnostic questions are never returned; they are served only by the
     * assessment module. All filters are optional and combined with AND.
     */
    @Transactional(readOnly = true)
    public List<QuestionSummaryDto> list(Long topicId, Long conceptId, Difficulty difficulty) {
        List<Specification<Question>> parts = new ArrayList<>();
        parts.add((root, query, cb) -> cb.or(
                cb.isNull(root.get("placementTags")),
                cb.notEqual(root.get("placementTags"), DataSeeder.DIAGNOSTIC_TAG)));
        if (topicId != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("topic").get("id"), topicId));
        }
        if (conceptId != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("concept").get("id"), conceptId));
        }
        if (difficulty != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("difficulty"), difficulty));
        }
        Specification<Question> spec = Specification.allOf(parts);

        return questionRepository.findAll(spec, Sort.by("id")).stream()
                .filter(question -> !isDiagnostic(question))
                .map(QuestionSummaryDto::from)
                .toList();
    }

    /** Problem detail with PUBLIC test cases only. Diagnostic slugs look like "not found". */
    @Transactional(readOnly = true)
    public QuestionDetailDto getBySlug(String slug) {
        Question question = questionRepository.findBySlug(slug)
                .filter(found -> !isDiagnostic(found))
                .orElseThrow(() -> ResourceNotFoundException.of("Question", slug));
        List<PublicTestCaseDto> samples = PublicTestCaseDto.publicOnly(
                testCaseRepository.findByQuestionIdAndHiddenFalse(question.getId()));
        return QuestionDetailDto.from(question, samples);
    }

    private static boolean isDiagnostic(Question question) {
        return DataSeeder.DIAGNOSTIC_TAG.equals(question.getPlacementTags());
    }
}
