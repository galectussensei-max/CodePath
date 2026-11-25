package com.codepath.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.codepath.TestData;
import com.codepath.questions.Topic;
import com.codepath.questions.TopicPrerequisite;
import com.codepath.users.User;

class TopicGraphTest {

    private final TestData data = new TestData();
    private final User user = TestData.user(1L);

    @Test
    void prerequisiteIsMetOnlyWhenAssessedAndAtOrAboveThreshold() {
        assertFalse(TopicGraph.isMet(null));
        assertFalse(TopicGraph.isMet(TestData.mastery(user, data.arrays, 80, MasteryStatus.UNASSESSED, null)));
        assertFalse(TopicGraph.isMet(TestData.mastery(user, data.arrays, 39.9, MasteryStatus.WEAK, null)));
        assertTrue(TopicGraph.isMet(TestData.mastery(user, data.arrays, 40.0, MasteryStatus.DEVELOPING, null)));
    }

    @Test
    void reportsUnmetPrerequisites() {
        TopicGraph graph = TopicGraph.build(data.topics(), data.links());
        Map<Long, TopicMastery> mastery = Map.of(
                data.arrays.getId(), TestData.mastery(user, data.arrays, 75, MasteryStatus.STRONG, null));

        assertTrue(graph.unmetPrerequisites(data.arrays.getId(), mastery).isEmpty());
        assertTrue(graph.unmetPrerequisites(data.strings.getId(), mastery).isEmpty());
        assertEquals(List.of(data.strings.getId()), graph.unmetPrerequisites(data.dp.getId(), mastery));
        assertEquals(List.of(data.arrays.getId(), data.strings.getId()),
                graph.unmetPrerequisites(data.dp.getId(), Map.of()));
    }

    @Test
    void topologicalOrderPutsPrerequisitesFirstEvenWhenDisplayOrderDisagrees() {
        data.arrays.setDisplayOrder(3);
        data.strings.setDisplayOrder(2);
        data.dp.setDisplayOrder(1);
        TopicGraph graph = TopicGraph.build(data.topics(), data.links());

        List<String> names = graph.topologicalOrder().stream().map(Topic::getName).toList();

        assertEquals(List.of("Arrays", "Strings", "Dynamic Programming"), names);
    }

    @Test
    void cyclesDoNotLoopForeverAndEveryTopicIsStillReturned() {
        List<TopicPrerequisite> cyclic = List.of(
                TopicPrerequisite.link(data.arrays, data.strings),
                TopicPrerequisite.link(data.strings, data.arrays));

        TopicGraph graph = TopicGraph.build(data.topics(), cyclic);

        assertEquals(3, graph.topologicalOrder().size());
    }

    @Test
    void ignoresLinksToUnknownTopicsAndSelfLinks() {
        Topic ghost = new Topic();
        ghost.setId(99L);
        ghost.setName("Ghost");
        List<TopicPrerequisite> links = List.of(
                TopicPrerequisite.link(data.arrays, ghost),
                TopicPrerequisite.link(data.strings, data.strings));

        TopicGraph graph = TopicGraph.build(data.topics(), links);

        assertTrue(graph.prerequisitesOf(data.arrays.getId()).isEmpty());
        assertTrue(graph.prerequisitesOf(data.strings.getId()).isEmpty());
    }
}
