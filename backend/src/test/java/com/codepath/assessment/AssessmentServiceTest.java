package com.codepath.assessment;

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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.codepath.common.ConflictException;
import com.codepath.config.DataSeeder;
import com.codepath.learning.MasteryStatus;
import com.codepath.learning.TopicMastery;
import com.codepath.learning.TopicMasteryRepository;
import com.codepath.questions.Difficulty;
import com.codepath.questions.Question;
import com.codepath.questions.QuestionRepository;
import com.codepath.questions.TestCase;
import com.codepath.questions.TestCaseRepository;
import com.codepath.questions.Topic;
import com.codepath.questions.TopicRepository;
import com.codepath.users.LearnerProfile;
import com.codepath.users.LearnerProfileRepository;
import com.codepath.users.Role;
import com.codepath.users.User;
import com.codepath.users.UserRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssessmentServiceTest {

    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private TestCaseRepository testCaseRepository;
    @Mock
    private TopicRepository topicRepository;
    @Mock
    private TopicMasteryRepository topicMasteryRepository;
    @Mock
    private LearnerProfileRepository learnerProfileRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AssessmentService service;

    private Topic arrays;
    private Topic strings;
    private Topic stacks;
    private LearnerProfile profile;

    @BeforeEach
    void setUp() {
        arrays = topic(1L, "Arrays", 1);
        strings = topic(2L, "Strings", 2);
        stacks = topic(3L, "Stacks", 3); // no diagnostic question -> stays UNASSESSED

        User user = new User();
        user.setId(7L);
        user.setRole(Role.STUDENT);
        profile = new LearnerProfile();
        profile.setUser(user);

        Question a1 = diagnostic(10L, arrays, Difficulty.EASY, "C");
        Question a2 = diagnostic(11L, arrays, Difficulty.EASY, "B");
        Question s1 = diagnostic(12L, strings, Difficulty.EASY, "A");

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(learnerProfileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));
        when(questionRepository.findByPlacementTagsOrderByIdAsc(DataSeeder.DIAGNOSTIC_TAG))
                .thenReturn(List.of(a1, a2, s1));
        when(topicRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(arrays, strings, stacks));
        when(topicMasteryRepository.findByUserIdAndTopicId(any(), any())).thenReturn(Optional.empty());
    }

    private static Topic topic(Long id, String name, int order) {
        Topic topic = new Topic();
        topic.setId(id);
        topic.setName(name);
        topic.setDisplayOrder(order);
        return topic;
    }

    private Question diagnostic(Long id, Topic topic, Difficulty difficulty, String correct) {
        Question question = DiagnosticQuestionMapperTest.question(DiagnosticQuestionMapperTest.DESCRIPTION);
        question.setId(id);
        question.setSlug("diag-" + id);
        question.setTopic(topic);
        question.setDifficulty(difficulty);

        TestCase key = new TestCase();
        key.setQuestion(question);
        key.setInputData("N/A");
        key.setExpectedOutput(correct);
        key.setHidden(true);
        when(testCaseRepository.findByQuestionId(id)).thenReturn(List.of(key));
        return question;
    }

    private static DiagnosticSubmitRequest answers(String a1, String a2, String s1) {
        return new DiagnosticSubmitRequest(List.of(
                new DiagnosticSubmitRequest.AnswerItem(10L, a1),
                new DiagnosticSubmitRequest.AnswerItem(11L, a2),
                new DiagnosticSubmitRequest.AnswerItem(12L, s1)));
    }

    @Test
    void servedQuestionsDoNotContainTheAnswerKey() {
        List<DiagnosticQuestionDto> dtos = service.getDiagnosticQuestions();

        assertEquals(3, dtos.size());
        assertEquals(4, dtos.get(0).options().size());
        verify(testCaseRepository, never()).findByQuestionId(any());
    }

    @Test
    void gradesPerTopicAndInitializesMasteryForEveryTopic() {
        // Arrays: first right (C), second wrong; Strings: right (a is accepted case-insensitively).
        DiagnosticResultDto result = service.submit(7L, answers("C", "D", "a"));

        assertEquals(3, result.totalQuestions());
        assertEquals(2, result.correctCount());
        assertEquals(3, result.topics().size());

        DiagnosticResultDto.TopicResult arraysResult = result.topics().get(0);
        assertEquals(1, arraysResult.correct());
        assertEquals(2, arraysResult.total());
        assertEquals(50.0, arraysResult.masteryScore());
        assertEquals(MasteryStatus.DEVELOPING, arraysResult.status());

        DiagnosticResultDto.TopicResult stringsResult = result.topics().get(1);
        assertEquals(AssessmentService.INITIAL_SCORE_CAP, stringsResult.masteryScore());
        assertEquals(MasteryStatus.STRONG, stringsResult.status());

        DiagnosticResultDto.TopicResult stacksResult = result.topics().get(2);
        assertEquals(0, stacksResult.total());
        assertEquals(MasteryStatus.UNASSESSED, stacksResult.status());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TopicMastery>> captor = ArgumentCaptor.forClass(List.class);
        verify(topicMasteryRepository).saveAll(captor.capture());
        assertEquals(3, captor.getValue().size());
        assertTrue(profile.isDiagnosticCompleted());
        verify(learnerProfileRepository).save(profile);
    }

    @Test
    void allWrongAnswersGiveWeakTopics() {
        DiagnosticResultDto result = service.submit(7L, answers("A", "A", "D"));

        assertEquals(0, result.correctCount());
        assertEquals(MasteryStatus.WEAK, result.topics().get(0).status());
        assertEquals(MasteryStatus.WEAK, result.topics().get(1).status());
        assertEquals(0.0, result.topics().get(0).masteryScore());
    }

    @Test
    void secondSubmissionIsRejectedAndNothingIsOverwritten() {
        profile.setDiagnosticCompleted(true);

        assertThrows(ConflictException.class, () -> service.submit(7L, answers("C", "B", "A")));
        verify(topicMasteryRepository, never()).saveAll(any());
    }

    @Test
    void incompleteAnswersAreRejected() {
        DiagnosticSubmitRequest partial = new DiagnosticSubmitRequest(List.of(
                new DiagnosticSubmitRequest.AnswerItem(10L, "C")));

        assertThrows(IllegalArgumentException.class, () -> service.submit(7L, partial));
        assertFalse(profile.isDiagnosticCompleted());
        verify(topicMasteryRepository, never()).saveAll(any());
    }

    @Test
    void duplicateOrUnknownQuestionIdsAreRejected() {
        DiagnosticSubmitRequest duplicate = new DiagnosticSubmitRequest(List.of(
                new DiagnosticSubmitRequest.AnswerItem(10L, "C"),
                new DiagnosticSubmitRequest.AnswerItem(10L, "B"),
                new DiagnosticSubmitRequest.AnswerItem(12L, "A")));
        DiagnosticSubmitRequest unknown = new DiagnosticSubmitRequest(List.of(
                new DiagnosticSubmitRequest.AnswerItem(10L, "C"),
                new DiagnosticSubmitRequest.AnswerItem(11L, "B"),
                new DiagnosticSubmitRequest.AnswerItem(999L, "A")));

        assertThrows(IllegalArgumentException.class, () -> service.submit(7L, duplicate));
        assertThrows(IllegalArgumentException.class, () -> service.submit(7L, unknown));
        verify(topicMasteryRepository, never()).saveAll(any());
    }

    @Test
    void masteryStatusThresholds() {
        assertEquals(MasteryStatus.WEAK, MasteryStatus.fromScore(39.9));
        assertEquals(MasteryStatus.DEVELOPING, MasteryStatus.fromScore(40.0));
        assertEquals(MasteryStatus.DEVELOPING, MasteryStatus.fromScore(69.9));
        assertEquals(MasteryStatus.STRONG, MasteryStatus.fromScore(70.0));
    }
}
