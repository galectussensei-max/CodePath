package com.codepath;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.codepath.ai.AiInteraction;
import com.codepath.discussion.Discussion;
import com.codepath.discussion.DiscussionComment;
import com.codepath.learning.TopicMastery;
import com.codepath.questions.Concept;
import com.codepath.questions.Question;
import com.codepath.questions.TestCase;
import com.codepath.questions.Topic;
import com.codepath.questions.TopicPrerequisite;
import com.codepath.submissions.Submission;
import com.codepath.users.LearnerProfile;
import com.codepath.users.User;

import jakarta.persistence.Table;

/** Confirms the 12 Blueprint Section 4 tables are all mapped, without needing a database. */
class BlueprintSchemaTest {

    @Test
    void allTwelveBlueprintTablesAreMapped() {
        Set<String> mapped = Stream.of(
                        User.class, LearnerProfile.class, Topic.class, TopicPrerequisite.class,
                        Concept.class, Question.class, TestCase.class, Submission.class,
                        TopicMastery.class, AiInteraction.class, Discussion.class, DiscussionComment.class)
                .map(type -> type.getAnnotation(Table.class).name())
                .collect(Collectors.toSet());

        assertThat(mapped).containsExactlyInAnyOrder(
                "users", "learner_profiles", "topics", "topic_prerequisites",
                "concepts", "questions", "test_cases", "submissions",
                "topic_mastery", "ai_interactions", "discussions", "discussion_comments");
    }
}
