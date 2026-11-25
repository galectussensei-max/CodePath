package com.codepath.questions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.codepath.common.ResourceNotFoundException;
import com.codepath.config.DataSeeder;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuestionServiceTest {

    private static final String SECRET = "SECRET-HIDDEN-OUTPUT";

    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private TestCaseRepository testCaseRepository;

    @InjectMocks
    private QuestionService service;

    private Question problem;
    private Question diagnostic;

    @BeforeEach
    void setUp() {
        Topic topic = new Topic();
        topic.setId(1L);
        topic.setName("Arrays");
        Concept concept = new Concept();
        concept.setId(2L);
        concept.setName("Two Pointers");
        concept.setTopic(topic);

        problem = question(10L, "two-sum-sorted", "EASY,AMAZON", topic, concept);
        diagnostic = question(11L, "diag-arrays", DataSeeder.DIAGNOSTIC_TAG, topic, concept);

        when(questionRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(problem, diagnostic));
        when(questionRepository.findBySlug("two-sum-sorted")).thenReturn(Optional.of(problem));
        when(questionRepository.findBySlug("diag-arrays")).thenReturn(Optional.of(diagnostic));
    }

    private static Question question(Long id, String slug, String tags, Topic topic, Concept concept) {
        Question question = new Question();
        question.setId(id);
        question.setSlug(slug);
        question.setTitle("Title " + slug);
        question.setTopic(topic);
        question.setConcept(concept);
        question.setDifficulty(Difficulty.EASY);
        question.setPlacementTags(tags);
        question.setDescription("Describe " + slug);
        question.setConstraints("1 <= n <= 10");
        question.setStarterCodeJava("public class Main {}");
        question.setTimeLimitMs(2000);
        question.setMemoryLimitKb(262144);
        return question;
    }

    private static TestCase testCase(Long id, String output, boolean hidden) {
        TestCase testCase = new TestCase();
        testCase.setId(id);
        testCase.setInputData("in-" + id);
        testCase.setExpectedOutput(output);
        testCase.setHidden(hidden);
        return testCase;
    }

    @Test
    void listNeverReturnsDiagnosticQuestions() {
        List<QuestionSummaryDto> result = service.list(null, null, null);

        assertEquals(1, result.size());
        assertEquals("two-sum-sorted", result.get(0).slug());
        assertEquals("Arrays", result.get(0).topic());
        assertEquals("Two Pointers", result.get(0).concept());
        assertEquals(List.of("EASY", "AMAZON"), result.get(0).placementTags());
    }

    @Test
    void detailContainsOnlyPublicCasesEvenIfRepositoryReturnsHiddenOnes() throws Exception {
        when(testCaseRepository.findByQuestionIdAndHiddenFalse(10L))
                .thenReturn(List.of(testCase(1L, "public-out", false), testCase(2L, SECRET, true)));

        QuestionDetailDto detail = service.getBySlug("two-sum-sorted");

        assertEquals(1, detail.sampleTestCases().size());
        assertEquals("public-out", detail.sampleTestCases().get(0).expectedOutput());
        String json = new ObjectMapper().writeValueAsString(detail);
        assertFalse(json.contains(SECRET));
        assertFalse(json.toLowerCase().contains("hidden"));
        assertTrue(json.contains("public-out"));
        verify(testCaseRepository, never()).findByQuestionId(any());
    }

    @Test
    void diagnosticAndUnknownSlugsAreNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.getBySlug("diag-arrays"));
        assertThrows(ResourceNotFoundException.class, () -> service.getBySlug("no-such-problem"));
    }
}
