package com.codepath;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.codepath.learning.MasteryStatus;
import com.codepath.learning.TopicMastery;
import com.codepath.questions.Concept;
import com.codepath.questions.Difficulty;
import com.codepath.questions.Question;
import com.codepath.questions.Topic;
import com.codepath.questions.TopicPrerequisite;
import com.codepath.users.User;

/**
 * A small curriculum shared by the Phase 4 tests:
 * Arrays (root) -> Strings (needs Arrays) -> Dynamic Programming (needs Arrays and Strings).
 */
public final class TestData {

    public final Topic arrays = topic(1L, "Arrays", 1);
    public final Topic strings = topic(2L, "Strings", 2);
    public final Topic dp = topic(3L, "Dynamic Programming", 3);

    public final Concept twoPointers = concept(1L, "Two Pointers", arrays);
    public final Concept slidingWindow = concept(2L, "Sliding Window", arrays);
    public final Concept palindromes = concept(3L, "Palindromes", strings);
    public final Concept oneDimDp = concept(4L, "1D Dynamic Programming", dp);

    public final Question a1 = question(1L, "arrays-easy-tp", arrays, twoPointers, Difficulty.EASY);
    public final Question a2 = question(2L, "arrays-medium-tp", arrays, twoPointers, Difficulty.MEDIUM);
    public final Question a3 = question(3L, "arrays-hard-sw", arrays, slidingWindow, Difficulty.HARD);
    public final Question a4 = question(4L, "arrays-easy-sw", arrays, slidingWindow, Difficulty.EASY);
    public final Question s1 = question(5L, "strings-easy", strings, palindromes, Difficulty.EASY);
    public final Question s2 = question(6L, "strings-medium", strings, palindromes, Difficulty.MEDIUM);
    public final Question d1 = question(7L, "dp-easy", dp, oneDimDp, Difficulty.EASY);
    public final Question d2 = question(8L, "dp-medium", dp, oneDimDp, Difficulty.MEDIUM);
    public final Question d3 = question(9L, "dp-hard", dp, oneDimDp, Difficulty.HARD);

    public List<Topic> topics() {
        return new ArrayList<>(List.of(arrays, strings, dp));
    }

    public List<TopicPrerequisite> links() {
        return new ArrayList<>(List.of(
                TopicPrerequisite.link(strings, arrays),
                TopicPrerequisite.link(dp, arrays),
                TopicPrerequisite.link(dp, strings)));
    }

    public List<Question> questions() {
        return new ArrayList<>(List.of(a1, a2, a3, a4, s1, s2, d1, d2, d3));
    }

    public static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    public static TopicMastery mastery(User user, Topic topic, double score, MasteryStatus status, Instant nextDue) {
        TopicMastery row = new TopicMastery();
        row.setUser(user);
        row.setTopic(topic);
        row.setMasteryScore(score);
        row.setStatus(status);
        row.setAttemptsCount(1);
        row.setNextRevisionDue(nextDue);
        return row;
    }

    private static Topic topic(Long id, String name, int order) {
        Topic topic = new Topic();
        topic.setId(id);
        topic.setName(name);
        topic.setDescription(name + " description");
        topic.setDisplayOrder(order);
        return topic;
    }

    private static Concept concept(Long id, String name, Topic topic) {
        Concept concept = new Concept();
        concept.setId(id);
        concept.setName(name);
        concept.setTopic(topic);
        return concept;
    }

    private static Question question(Long id, String slug, Topic topic, Concept concept, Difficulty difficulty) {
        Question question = new Question();
        question.setId(id);
        question.setSlug(slug);
        question.setTitle(slug);
        question.setTopic(topic);
        question.setConcept(concept);
        question.setDifficulty(difficulty);
        return question;
    }
}
