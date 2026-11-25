package com.codepath.assessment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codepath.common.ConflictException;
import com.codepath.common.ResourceNotFoundException;
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
import com.codepath.users.User;
import com.codepath.users.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Serves and grades the diagnostic. Diagnostic questions are {@code questions} rows tagged
 * {@value DataSeeder#DIAGNOSTIC_TAG}; the correct letter is the {@code expected_output} of the
 * single HIDDEN test case and is only ever read here, server-side.
 */
@Service
@RequiredArgsConstructor
public class AssessmentService {

    /**
     * A short diagnostic cannot justify certainty, so the initial estimate never exceeds this score
     * (which still maps to STRONG). Practice on real problems can raise it later.
     */
    static final double INITIAL_SCORE_CAP = 75.0;

    static final String RESULT_NOTE = "Initial mastery values are rule-based estimates from a short diagnostic, "
            + "not measurements of true ability. They are refined as you solve problems.";

    private final QuestionRepository questionRepository;
    private final TestCaseRepository testCaseRepository;
    private final TopicRepository topicRepository;
    private final TopicMasteryRepository topicMasteryRepository;
    private final LearnerProfileRepository learnerProfileRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<DiagnosticQuestionDto> getDiagnosticQuestions() {
        return loadDiagnosticQuestions().stream().map(DiagnosticQuestionMapper::toDto).toList();
    }

    @Transactional
    public DiagnosticResultDto submit(Long userId, DiagnosticSubmitRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        LearnerProfile profile = learnerProfileRepository.findByUserId(userId).orElseGet(() -> {
            LearnerProfile created = new LearnerProfile();
            created.setUser(user);
            return created;
        });
        if (profile.isDiagnosticCompleted()) {
            throw new ConflictException("Diagnostic already completed");
        }

        List<Question> questions = loadDiagnosticQuestions();
        Map<Long, String> answers = indexAnswers(request);
        Set<Long> expectedIds = questions.stream().map(Question::getId).collect(Collectors.toSet());
        if (!answers.keySet().equals(expectedIds)) {
            throw new IllegalArgumentException(
                    "Answer every diagnostic question exactly once (expected " + expectedIds.size() + " answers)");
        }

        Map<Long, TopicTally> tallies = new LinkedHashMap<>();
        for (Question question : questions) {
            boolean correct = answers.get(question.getId()).equals(correctOption(question));
            Topic topic = question.getTopic();
            TopicTally tally = tallies.computeIfAbsent(topic.getId(), id -> new TopicTally());
            double weight = weightOf(question.getDifficulty());
            tally.total++;
            tally.totalWeight += weight;
            if (correct) {
                tally.correct++;
                tally.earnedWeight += weight;
            }
        }

        List<TopicMastery> rows = new ArrayList<>();
        List<DiagnosticResultDto.TopicResult> topicResults = new ArrayList<>();
        int correctCount = 0;
        for (Topic topic : topicRepository.findAllByOrderByDisplayOrderAsc()) {
            TopicTally tally = tallies.get(topic.getId());
            double score = 0.0;
            MasteryStatus status = MasteryStatus.UNASSESSED;
            int correct = 0;
            int total = 0;
            if (tally != null) {
                score = tally.score();
                status = MasteryStatus.fromScore(score);
                correct = tally.correct;
                total = tally.total;
                correctCount += correct;
            }

            TopicMastery mastery = topicMasteryRepository.findByUserIdAndTopicId(userId, topic.getId())
                    .orElseGet(TopicMastery::new);
            mastery.setUser(user);
            mastery.setTopic(topic);
            mastery.setMasteryScore(score);
            mastery.setStatus(status);
            rows.add(mastery);
            topicResults.add(new DiagnosticResultDto.TopicResult(
                    topic.getId(), topic.getName(), correct, total, score, status));
        }
        topicMasteryRepository.saveAll(rows);

        profile.setDiagnosticCompleted(true);
        learnerProfileRepository.save(profile);

        return new DiagnosticResultDto(questions.size(), correctCount, topicResults, RESULT_NOTE);
    }

    private List<Question> loadDiagnosticQuestions() {
        return questionRepository.findByPlacementTagsOrderByIdAsc(DataSeeder.DIAGNOSTIC_TAG);
    }

    private static Map<Long, String> indexAnswers(DiagnosticSubmitRequest request) {
        Map<Long, String> answers = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        for (DiagnosticSubmitRequest.AnswerItem item : request.answers()) {
            if (!seen.add(item.questionId())) {
                throw new IllegalArgumentException("Duplicate answer for question " + item.questionId());
            }
            answers.put(item.questionId(), item.answer().strip().toUpperCase(Locale.ROOT));
        }
        return answers;
    }

    /** The answer key: the expected output of the diagnostic's single hidden test case. */
    private String correctOption(Question question) {
        List<TestCase> hidden = testCaseRepository.findByQuestionId(question.getId()).stream()
                .filter(TestCase::isHidden)
                .toList();
        if (hidden.size() != 1) {
            throw new IllegalStateException(
                    "Diagnostic question '" + question.getSlug() + "' must have exactly one hidden answer-key case");
        }
        return hidden.get(0).getExpectedOutput().strip().toUpperCase(Locale.ROOT);
    }

    private static double weightOf(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 1.0;
            case MEDIUM -> 1.5;
            case HARD -> 2.0;
        };
    }

    /** Difficulty-weighted share of correct answers, capped and rounded to one decimal. */
    private static final class TopicTally {
        int correct;
        int total;
        double earnedWeight;
        double totalWeight;

        double score() {
            double raw = totalWeight == 0 ? 0.0 : (earnedWeight / totalWeight) * 100.0;
            return Math.round(Math.min(raw, INITIAL_SCORE_CAP) * 10.0) / 10.0;
        }
    }
}
