package com.codepath.learning;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codepath.config.DataSeeder;
import com.codepath.questions.Difficulty;
import com.codepath.questions.Question;
import com.codepath.questions.QuestionRepository;
import com.codepath.questions.QuestionSummaryDto;
import com.codepath.questions.Topic;
import com.codepath.questions.TopicPrerequisiteRepository;
import com.codepath.questions.TopicRepository;
import com.codepath.submissions.SubmissionRepository;
import com.codepath.submissions.Verdict;

import lombok.RequiredArgsConstructor;

/**
 * Ranks unsolved problems for a learner with fixed, explainable rules (no machine learning).
 *
 * <p>Only problems whose topic prerequisites are met are candidates (see {@link TopicGraph}). Each candidate
 * scores up to 100 points:
 * <ul>
 *   <li>Topic need, 0 to {@value #NEED_MAX}: the lower the topic's mastery, the higher the score.</li>
 *   <li>Difficulty fit, 0 to {@value #FIT_EXACT}: full points when the difficulty matches the level implied
 *       by topic mastery (WEAK: Easy, DEVELOPING: Medium, STRONG: Hard), half when one step away.</li>
 *   <li>Concept gap, 0 to {@value #CONCEPT_MAX}: higher when few problems of the concept are solved.</li>
 *   <li>Revision bonus {@value #REVISION_BONUS}: the topic is past its revision date.</li>
 *   <li>Retry bonus {@value #RETRY_BONUS}: the learner already tried this problem without solving it.</li>
 * </ul>
 * Every returned problem carries a human-readable {@code reason}.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    public static final int DEFAULT_LIMIT = 5;
    public static final int MAX_LIMIT = 20;

    static final double NEED_MAX = 50.0;
    static final double FIT_EXACT = 30.0;
    static final double FIT_ADJACENT = 15.0;
    static final double CONCEPT_MAX = 10.0;
    static final double REVISION_BONUS = 10.0;
    static final double RETRY_BONUS = 5.0;
    static final int MAX_PER_CONCEPT = 2;

    private final QuestionRepository questionRepository;
    private final TopicRepository topicRepository;
    private final TopicPrerequisiteRepository topicPrerequisiteRepository;
    private final TopicMasteryRepository topicMasteryRepository;
    private final SubmissionRepository submissionRepository;

    @Transactional(readOnly = true)
    public RecommendationsDto recommend(Long userId, int requestedLimit) {
        int limit = requestedLimit <= 0 ? DEFAULT_LIMIT : Math.min(requestedLimit, MAX_LIMIT);
        Instant now = Instant.now();

        List<Topic> topics = topicRepository.findAllByOrderByDisplayOrderAsc();
        TopicGraph graph = TopicGraph.build(topics, topicPrerequisiteRepository.findAll());
        Map<Long, TopicMastery> mastery = topicMasteryRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(row -> row.getTopic().getId(), row -> row, (a, b) -> a));
        Set<Long> solved = new HashSet<>(submissionRepository.findQuestionIdsByVerdict(userId, Verdict.ACCEPTED));
        Set<Long> attempted = new HashSet<>(submissionRepository.findAttemptedQuestionIds(userId));
        List<Question> practice = questionRepository.findPracticeQuestions(DataSeeder.DIAGNOSTIC_TAG);

        Map<Long, Integer> conceptTotal = new HashMap<>();
        Map<Long, Integer> conceptSolved = new HashMap<>();
        for (Question question : practice) {
            Long conceptId = question.getConcept().getId();
            conceptTotal.merge(conceptId, 1, Integer::sum);
            if (solved.contains(question.getId())) {
                conceptSolved.merge(conceptId, 1, Integer::sum);
            }
        }

        List<Scored> candidates = new ArrayList<>();
        for (Question question : practice) {
            if (solved.contains(question.getId())) {
                continue;
            }
            Long topicId = question.getTopic().getId();
            if (!graph.unmetPrerequisites(topicId, mastery).isEmpty()) {
                continue;
            }
            TopicMastery row = mastery.get(topicId);
            Long conceptId = question.getConcept().getId();
            int total = conceptTotal.getOrDefault(conceptId, 1);
            int solvedInConcept = conceptSolved.getOrDefault(conceptId, 0);
            boolean revisionDue = row != null && row.getNextRevisionDue() != null
                    && row.getNextRevisionDue().isBefore(now);
            boolean retry = attempted.contains(question.getId());
            candidates.add(score(question, graph, mastery, solvedInConcept, total, revisionDue, retry));
        }

        candidates.sort(Comparator.comparingDouble(Scored::score).reversed()
                .thenComparing(c -> c.question().getDifficulty())
                .thenComparing(c -> c.question().getId()));

        List<Scored> chosen = pickWithConceptCap(candidates, limit);
        List<RecommendationDto> items = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            Question q = chosen.get(i).question();
            QuestionSummaryDto summary = QuestionSummaryDto.from(q);
            items.add(new RecommendationDto(i + 1, q.getId(), q.getSlug(), q.getTitle(), summary.topicId(),
                    summary.topic(), summary.concept(), q.getDifficulty(), summary.placementTags(),
                    Math.round(chosen.get(i).score() * 10.0) / 10.0, chosen.get(i).reason()));
        }

        return new RecommendationsDto(items, lockedTopics(graph, mastery), MasteryUpdate.ESTIMATE_NOTE
                + " Recommendations follow fixed rules: weaker topics first, difficulty matched to your estimated"
                + " level, and only topics whose prerequisites you have met.");
    }

    private Scored score(Question question, TopicGraph graph, Map<Long, TopicMastery> mastery,
            int solvedInConcept, int totalInConcept, boolean revisionDue, boolean retry) {
        Topic topic = question.getTopic();
        TopicMastery row = mastery.get(topic.getId());
        double topicScore = row == null ? 0.0 : row.getMasteryScore();
        Difficulty target = targetDifficulty(topicScore);

        double need = (100.0 - topicScore) / 100.0 * NEED_MAX;
        int distance = Math.abs(question.getDifficulty().ordinal() - target.ordinal());
        double fit = distance == 0 ? FIT_EXACT : distance == 1 ? FIT_ADJACENT : 0.0;
        double conceptGap = CONCEPT_MAX * (1.0 - solvedInConcept / (double) Math.max(totalInConcept, 1));
        double total = need + fit + conceptGap + (revisionDue ? REVISION_BONUS : 0.0) + (retry ? RETRY_BONUS : 0.0);

        StringBuilder reason = new StringBuilder("Recommended because ");
        if (row == null || row.getStatus() == MasteryStatus.UNASSESSED) {
            reason.append(topic.getName()).append(" has not been assessed yet");
        } else {
            reason.append(topic.getName()).append(" mastery is ").append(percent(topicScore));
        }
        List<Long> prerequisites = graph.prerequisitesOf(topic.getId());
        if (prerequisites.isEmpty()) {
            reason.append(" (no prerequisites)");
        } else {
            String met = prerequisites.stream()
                    .map(id -> graph.topic(id).getName() + " " + percent(mastery.get(id).getMasteryScore()))
                    .collect(Collectors.joining(", "));
            reason.append(prerequisites.size() == 1 ? " (prerequisite met: " : " (prerequisites met: ")
                    .append(met).append(')');
        }
        String concept = question.getConcept().getName();
        if (solvedInConcept == 0) {
            reason.append(" and you have not solved any ").append(concept).append(" problems yet.");
        } else {
            reason.append(" and ").append(concept).append(" needs practice (").append(solvedInConcept)
                    .append(" of ").append(totalInConcept).append(" solved).");
        }
        reason.append(' ').append(capitalize(question.getDifficulty())).append(' ')
                .append(difficultyClause(distance, question.getDifficulty(), target));
        if (revisionDue) {
            reason.append(" This topic is due for revision.");
        }
        if (retry) {
            reason.append(" You tried this problem before without solving it.");
        }
        return new Scored(question, total, reason.toString());
    }

    /** First pass keeps at most {@value #MAX_PER_CONCEPT} problems per concept; leftovers only fill gaps. */
    private static List<Scored> pickWithConceptCap(List<Scored> sorted, int limit) {
        Set<Scored> chosen = new HashSet<>();
        Map<Long, Integer> perConcept = new HashMap<>();
        for (Scored candidate : sorted) {
            if (chosen.size() >= limit) {
                break;
            }
            Long conceptId = candidate.question().getConcept().getId();
            if (perConcept.getOrDefault(conceptId, 0) < MAX_PER_CONCEPT) {
                chosen.add(candidate);
                perConcept.merge(conceptId, 1, Integer::sum);
            }
        }
        for (Scored candidate : sorted) {
            if (chosen.size() >= limit) {
                break;
            }
            chosen.add(candidate);
        }
        return sorted.stream().filter(chosen::contains).toList();
    }

    private static List<LockedTopicDto> lockedTopics(TopicGraph graph, Map<Long, TopicMastery> mastery) {
        List<LockedTopicDto> locked = new ArrayList<>();
        for (Topic topic : graph.topics()) {
            List<Long> unmet = graph.unmetPrerequisites(topic.getId(), mastery);
            if (unmet.isEmpty()) {
                continue;
            }
            String needs = unmet.stream().map(id -> {
                TopicMastery row = mastery.get(id);
                String current = row == null || row.getStatus() == MasteryStatus.UNASSESSED
                        ? "not assessed yet" : "currently " + percent(row.getMasteryScore());
                return graph.topic(id).getName() + " reaches " + percent(TopicGraph.PREREQUISITE_MET_SCORE)
                        + " (" + current + ")";
            }).collect(Collectors.joining(", "));
            locked.add(new LockedTopicDto(topic.getId(), topic.getName(), "Locked until " + needs));
        }
        return locked;
    }

    /** The difficulty a learner at this topic score should practise next. */
    static Difficulty targetDifficulty(double topicScore) {
        if (topicScore < MasteryStatus.WEAK_BELOW) {
            return Difficulty.EASY;
        }
        return topicScore < MasteryStatus.STRONG_FROM ? Difficulty.MEDIUM : Difficulty.HARD;
    }

    private static String difficultyClause(int distance, Difficulty actual, Difficulty target) {
        if (distance == 0) {
            return "difficulty matches your current level in this topic.";
        }
        return actual.ordinal() > target.ordinal()
                ? "difficulty is a stretch above your current level in this topic."
                : "difficulty is a lighter step below your current level in this topic.";
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.0f%%", value);
    }

    private static String capitalize(Difficulty difficulty) {
        String name = difficulty.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    private record Scored(Question question, double score, String reason) {
    }
}
