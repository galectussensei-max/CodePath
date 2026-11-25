package com.codepath.learning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.codepath.questions.Topic;
import com.codepath.questions.TopicPrerequisite;

/**
 * The topic prerequisite graph, shared by recommendations, the roadmap and analytics so that all of them
 * apply the same unlock rule: a prerequisite is <em>met</em> once the learner has been assessed in it and its
 * mastery score has reached {@value #PREREQUISITE_MET_SCORE} (the point where a topic stops being WEAK).
 */
public final class TopicGraph {

    /** Mastery score a prerequisite topic must reach before topics that depend on it are unlocked. */
    public static final double PREREQUISITE_MET_SCORE = MasteryStatus.WEAK_BELOW;

    private final Map<Long, Topic> topicsById = new LinkedHashMap<>();
    private final Map<Long, List<Long>> prerequisitesByTopic = new LinkedHashMap<>();

    private TopicGraph() {
    }

    /** Builds the graph. Links that mention an unknown topic are ignored; topics are kept in display order. */
    public static TopicGraph build(List<Topic> topics, List<TopicPrerequisite> links) {
        TopicGraph graph = new TopicGraph();
        topics.stream()
                .sorted(Comparator.comparing(Topic::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Topic::getId))
                .forEach(topic -> {
                    graph.topicsById.put(topic.getId(), topic);
                    graph.prerequisitesByTopic.put(topic.getId(), new ArrayList<>());
                });
        for (TopicPrerequisite link : links) {
            Long topicId = link.getId().getTopicId();
            Long prerequisiteId = link.getId().getPrerequisiteTopicId();
            if (graph.topicsById.containsKey(topicId) && graph.topicsById.containsKey(prerequisiteId)
                    && !topicId.equals(prerequisiteId)
                    && !graph.prerequisitesByTopic.get(topicId).contains(prerequisiteId)) {
                graph.prerequisitesByTopic.get(topicId).add(prerequisiteId);
            }
        }
        return graph;
    }

    /** Topics in display order. */
    public List<Topic> topics() {
        return List.copyOf(topicsById.values());
    }

    public Topic topic(Long topicId) {
        return topicsById.get(topicId);
    }

    public List<Long> prerequisitesOf(Long topicId) {
        return List.copyOf(prerequisitesByTopic.getOrDefault(topicId, List.of()));
    }

    /** A prerequisite counts as met only when the learner was assessed and reached the threshold. */
    public static boolean isMet(TopicMastery mastery) {
        return mastery != null
                && mastery.getStatus() != MasteryStatus.UNASSESSED
                && mastery.getMasteryScore() >= PREREQUISITE_MET_SCORE;
    }

    /** Ids of the prerequisites of the topic that the learner has not met yet (empty means unlocked). */
    public List<Long> unmetPrerequisites(Long topicId, Map<Long, TopicMastery> masteryByTopic) {
        return prerequisitesOf(topicId).stream()
                .filter(prerequisiteId -> !isMet(masteryByTopic.get(prerequisiteId)))
                .toList();
    }

    /**
     * Topics ordered so that every topic comes after its prerequisites; ties are broken by display order.
     * If the data ever contains a cycle the remaining topics are appended in display order instead of failing.
     */
    public List<Topic> topologicalOrder() {
        List<Topic> ordered = new ArrayList<>();
        Set<Long> placed = new HashSet<>();
        while (ordered.size() < topicsById.size()) {
            Topic next = null;
            for (Topic candidate : topicsById.values()) {
                if (!placed.contains(candidate.getId())
                        && placed.containsAll(prerequisitesByTopic.get(candidate.getId()))) {
                    next = candidate;
                    break;
                }
            }
            if (next == null) {
                next = topicsById.values().stream().filter(t -> !placed.contains(t.getId())).findFirst().orElseThrow();
            }
            ordered.add(next);
            placed.add(next.getId());
        }
        return ordered;
    }
}
