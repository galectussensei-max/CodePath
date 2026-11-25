package com.codepath.roadmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codepath.config.DataSeeder;
import com.codepath.learning.MasteryHeuristicService;
import com.codepath.learning.MasteryStatus;
import com.codepath.learning.MasteryUpdate;
import com.codepath.learning.TopicGraph;
import com.codepath.learning.TopicMastery;
import com.codepath.learning.TopicMasteryRepository;
import com.codepath.questions.Question;
import com.codepath.questions.QuestionRepository;
import com.codepath.questions.Topic;
import com.codepath.questions.TopicPrerequisiteRepository;
import com.codepath.questions.TopicRepository;
import com.codepath.submissions.SubmissionRepository;
import com.codepath.submissions.Verdict;
import com.codepath.users.LearnerProfile;
import com.codepath.users.LearnerProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * Builds the optional learning pathway: topics in prerequisite order, each with its problems from Easy to
 * Hard and the learner's progress. The roadmap only advises; a LOCKED step explains what to strengthen first
 * but never stops anyone from practising. Pacing uses the learner's onboarding {@code daily_study_minutes}
 * and the same expected solving times as the mastery heuristic.
 */
@Service
@RequiredArgsConstructor
public class RoadmapService {

    static final String NOTE = "This roadmap is optional guidance in prerequisite order. " + MasteryUpdate.ESTIMATE_NOTE;

    private final TopicRepository topicRepository;
    private final TopicPrerequisiteRepository topicPrerequisiteRepository;
    private final QuestionRepository questionRepository;
    private final TopicMasteryRepository topicMasteryRepository;
    private final SubmissionRepository submissionRepository;
    private final LearnerProfileRepository learnerProfileRepository;

    @Transactional(readOnly = true)
    public RoadmapDto build(Long userId) {
        TopicGraph graph = TopicGraph.build(topicRepository.findAllByOrderByDisplayOrderAsc(),
                topicPrerequisiteRepository.findAll());
        Map<Long, TopicMastery> mastery = topicMasteryRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(row -> row.getTopic().getId(), row -> row, (a, b) -> a));
        Set<Long> solved = new HashSet<>(submissionRepository.findQuestionIdsByVerdict(userId, Verdict.ACCEPTED));
        List<Question> practice = questionRepository.findPracticeQuestions(DataSeeder.DIAGNOSTIC_TAG);
        LearnerProfile profile = learnerProfileRepository.findByUserId(userId).orElse(null);

        List<RoadmapDto.Step> steps = new ArrayList<>();
        int totalMinutesRemaining = 0;
        int order = 1;
        for (Topic topic : graph.topologicalOrder()) {
            List<Question> topicQuestions = practice.stream()
                    .filter(q -> q.getTopic().getId().equals(topic.getId()))
                    .sorted(Comparator.comparing(Question::getDifficulty).thenComparing(Question::getId))
                    .toList();
            List<RoadmapDto.Problem> problems = topicQuestions.stream()
                    .map(q -> new RoadmapDto.Problem(q.getId(), q.getSlug(), q.getTitle(), q.getConcept().getName(),
                            q.getDifficulty(), solved.contains(q.getId())))
                    .toList();
            int solvedCount = (int) problems.stream().filter(RoadmapDto.Problem::solved).count();
            int minutesRemaining = topicQuestions.stream()
                    .filter(q -> !solved.contains(q.getId()))
                    .mapToInt(q -> MasteryHeuristicService.expectedSeconds(q.getDifficulty()) / 60)
                    .sum();
            totalMinutesRemaining += minutesRemaining;

            TopicMastery row = mastery.get(topic.getId());
            List<Long> unmet = graph.unmetPrerequisites(topic.getId(), mastery);
            List<RoadmapDto.Prerequisite> prerequisites = graph.prerequisitesOf(topic.getId()).stream()
                    .map(id -> new RoadmapDto.Prerequisite(id, graph.topic(id).getName(),
                            mastery.containsKey(id) ? mastery.get(id).getMasteryScore() : 0.0,
                            TopicGraph.isMet(mastery.get(id))))
                    .toList();

            RoadmapStepState state = stateOf(unmet.isEmpty(), solvedCount, problems.size(), row);
            String lockedReason = unmet.isEmpty() ? null : "Recommended after you reach "
                    + String.format(Locale.ROOT, "%.0f%%", TopicGraph.PREREQUISITE_MET_SCORE) + " in "
                    + unmet.stream().map(id -> graph.topic(id).getName()).collect(Collectors.joining(", "));

            steps.add(new RoadmapDto.Step(order++, topic.getId(), topic.getName(), topic.getDescription(), state,
                    row == null ? 0.0 : row.getMasteryScore(),
                    row == null ? MasteryStatus.UNASSESSED : row.getStatus(),
                    solvedCount, problems.size(), minutesRemaining, prerequisites, lockedReason, problems));
        }

        Integer dailyMinutes = profile == null ? null : profile.getDailyStudyMinutes();
        Integer days = dailyMinutes == null || dailyMinutes <= 0
                ? null : (int) Math.ceil(totalMinutesRemaining / (double) dailyMinutes);
        RoadmapDto.Goal goal = new RoadmapDto.Goal(profile == null ? null : profile.getPrepGoal(),
                profile == null ? null : profile.getTargetLevel(), dailyMinutes);
        return new RoadmapDto(goal, steps, days, NOTE);
    }

    static RoadmapStepState stateOf(boolean unlocked, int solvedCount, int totalProblems, TopicMastery row) {
        if (totalProblems > 0 && solvedCount == totalProblems) {
            return RoadmapStepState.COMPLETED;
        }
        if (!unlocked) {
            return RoadmapStepState.LOCKED;
        }
        boolean started = solvedCount > 0 || (row != null && row.getAttemptsCount() > 0);
        return started ? RoadmapStepState.IN_PROGRESS : RoadmapStepState.AVAILABLE;
    }
}
