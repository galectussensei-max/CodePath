package com.codepath.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.codepath.questions.Concept;
import com.codepath.questions.ConceptRepository;
import com.codepath.questions.Question;
import com.codepath.questions.QuestionRepository;
import com.codepath.questions.TestCase;
import com.codepath.questions.TestCaseRepository;
import com.codepath.questions.Topic;
import com.codepath.questions.TopicPrerequisite;
import com.codepath.questions.TopicPrerequisiteRepository;
import com.codepath.questions.TopicRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataSeederTest {

    @Mock
    private TopicRepository topicRepository;
    @Mock
    private TopicPrerequisiteRepository topicPrerequisiteRepository;
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private TestCaseRepository testCaseRepository;

    private final List<Topic> topics = new ArrayList<>();
    private final List<TopicPrerequisite> prerequisites = new ArrayList<>();
    private final List<Concept> concepts = new ArrayList<>();
    private final List<Question> questions = new ArrayList<>();
    private final List<TestCase> testCases = new ArrayList<>();
    private long nextId = 1;

    private DataSeeder seeder;

    @BeforeEach
    void wireInMemoryRepositories() {
        when(topicRepository.count()).thenAnswer(invocation -> (long) topics.size());
        when(topicRepository.save(any(Topic.class))).thenAnswer(invocation -> {
            Topic topic = invocation.getArgument(0);
            topic.setId(nextId++);
            topics.add(topic);
            return topic;
        });
        when(topicPrerequisiteRepository.save(any(TopicPrerequisite.class))).thenAnswer(invocation -> {
            TopicPrerequisite row = invocation.getArgument(0);
            prerequisites.add(row);
            return row;
        });
        when(conceptRepository.count()).thenAnswer(invocation -> (long) concepts.size());
        when(conceptRepository.save(any(Concept.class))).thenAnswer(invocation -> {
            Concept concept = invocation.getArgument(0);
            concept.setId(nextId++);
            concepts.add(concept);
            return concept;
        });
        when(questionRepository.count()).thenAnswer(invocation -> (long) questions.size());
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(nextId++);
            questions.add(question);
            return question;
        });
        when(testCaseRepository.count()).thenAnswer(invocation -> (long) testCases.size());
        when(testCaseRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<TestCase> rows = invocation.getArgument(0);
            List<TestCase> saved = new ArrayList<>();
            for (TestCase row : rows) {
                row.setId(nextId++);
                testCases.add(row);
                saved.add(row);
            }
            return saved;
        });

        seeder = new DataSeeder(topicRepository, topicPrerequisiteRepository, conceptRepository,
                questionRepository, testCaseRepository);
    }

    private List<Question> problems() {
        return questions.stream().filter(q -> !isDiagnostic(q)).toList();
    }

    private List<Question> diagnostics() {
        return questions.stream().filter(DataSeederTest::isDiagnostic).toList();
    }

    private static boolean isDiagnostic(Question question) {
        return DataSeeder.DIAGNOSTIC_TAG.equals(question.getPlacementTags());
    }

    private List<TestCase> casesOf(Question question) {
        return testCases.stream().filter(tc -> tc.getQuestion() == question).toList();
    }

    @Test
    void seedsTheExpectedAmountOfData() {
        seeder.run();

        assertEquals(5, topics.size());
        assertEquals(10, concepts.size());
        assertEquals(15, problems().size());
        assertEquals(10, diagnostics().size());
    }

    @Test
    void prerequisitesFormAnAcyclicGraphOverSeededTopics() {
        seeder.run();

        assertFalse(prerequisites.isEmpty());
        Map<Long, Set<Long>> graph = prerequisites.stream().collect(Collectors.groupingBy(
                row -> row.getId().getTopicId(),
                Collectors.mapping(row -> row.getId().getPrerequisiteTopicId(), Collectors.toSet())));
        Set<Long> topicIds = topics.stream().map(Topic::getId).collect(Collectors.toSet());

        for (TopicPrerequisite row : prerequisites) {
            assertTrue(topicIds.contains(row.getId().getTopicId()));
            assertTrue(topicIds.contains(row.getId().getPrerequisiteTopicId()));
            assertFalse(row.getId().getTopicId().equals(row.getId().getPrerequisiteTopicId()));
        }
        for (Long start : graph.keySet()) {
            assertFalse(reaches(graph, start, start, new HashSet<>()), "cycle through topic " + start);
        }
    }

    private static boolean reaches(Map<Long, Set<Long>> graph, Long from, Long target, Set<Long> visited) {
        for (Long next : graph.getOrDefault(from, Set.of())) {
            if (next.equals(target)) {
                return true;
            }
            if (visited.add(next) && reaches(graph, next, target, visited)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void everyProblemHasPublicAndHiddenCasesAndStarterCode() {
        seeder.run();

        for (Question problem : problems()) {
            List<TestCase> cases = casesOf(problem);
            assertTrue(cases.stream().anyMatch(TestCase::isHidden), problem.getSlug() + " needs a hidden case");
            assertTrue(cases.stream().anyMatch(tc -> !tc.isHidden()), problem.getSlug() + " needs a public case");
            assertNotNull(problem.getStarterCodeJava(), problem.getSlug());
            assertTrue(problem.getStarterCodeJava().contains("public class Main"), problem.getSlug());
            assertNotNull(problem.getConcept());
            assertEquals(problem.getTopic(), problem.getConcept().getTopic(), problem.getSlug());
        }
    }

    @Test
    void diagnosticAnswerKeysAreHiddenAndOptionsAreInTheDescription() {
        seeder.run();

        for (Question diagnostic : diagnostics()) {
            List<TestCase> cases = casesOf(diagnostic);
            assertEquals(1, cases.size(), diagnostic.getSlug());
            TestCase key = cases.get(0);
            assertTrue(key.isHidden(), diagnostic.getSlug() + " answer key must be hidden");
            assertTrue(key.getExpectedOutput().matches("[A-D]"), diagnostic.getSlug());
            for (String letter : List.of("A.", "B.", "C.", "D.")) {
                assertTrue(diagnostic.getDescription().contains("\n" + letter + " "), diagnostic.getSlug() + " " + letter);
            }
        }
    }

    @Test
    void slugsAreUnique() {
        seeder.run();

        Set<String> slugs = questions.stream().map(Question::getSlug).collect(Collectors.toSet());
        assertEquals(questions.size(), slugs.size());
    }

    @Test
    void problemsCoverEveryDifficultyAndEveryTopic() {
        seeder.run();

        Set<String> difficulties = problems().stream().map(q -> q.getDifficulty().name()).collect(Collectors.toSet());
        assertEquals(Set.of("EASY", "MEDIUM", "HARD"), difficulties);
        Map<String, Long> perTopic = problems().stream()
                .collect(Collectors.groupingBy(q -> q.getTopic().getName(), Collectors.counting()));
        assertEquals(5, perTopic.size());
        perTopic.values().forEach(count -> assertEquals(3L, count));
    }

    @Test
    void skipsSeedingWhenTopicsAlreadyExist() {
        Topic existing = new Topic();
        existing.setName("Existing");
        topics.add(existing);

        seeder.run();

        verify(topicRepository, never()).save(any(Topic.class));
        verify(questionRepository, never()).save(any(Question.class));
        assertEquals(1, topics.size());
    }

    @Test
    void runningTwiceDoesNotDuplicateData() {
        seeder.run();
        int questionCount = questions.size();

        seeder.run();

        assertEquals(questionCount, questions.size());
        assertEquals(5, topics.size());
    }

    @Test
    void expectedOutputsDoNotCarryTrailingWhitespace() {
        seeder.run();

        Function<TestCase, String> output = TestCase::getExpectedOutput;
        for (TestCase testCase : testCases) {
            assertEquals(output.apply(testCase).strip(), output.apply(testCase));
        }
    }
}
