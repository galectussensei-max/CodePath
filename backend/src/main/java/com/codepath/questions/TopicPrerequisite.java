package com.codepath.questions;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "topic_prerequisites")
@Getter
@Setter
@NoArgsConstructor
public class TopicPrerequisite {

    @EmbeddedId
    private TopicPrerequisiteId id = new TopicPrerequisiteId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("topicId")
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("prerequisiteTopicId")
    @JoinColumn(name = "prerequisite_topic_id", nullable = false)
    private Topic prerequisiteTopic;

    public static TopicPrerequisite link(Topic topic, Topic prerequisite) {
        TopicPrerequisite row = new TopicPrerequisite();
        row.setTopic(topic);
        row.setPrerequisiteTopic(prerequisite);
        row.setId(new TopicPrerequisiteId(
                topic == null ? null : topic.getId(),
                prerequisite == null ? null : prerequisite.getId()));
        return row;
    }
}
