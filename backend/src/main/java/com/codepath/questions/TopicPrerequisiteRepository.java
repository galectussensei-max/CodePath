package com.codepath.questions;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicPrerequisiteRepository extends JpaRepository<TopicPrerequisite, TopicPrerequisiteId> {

    List<TopicPrerequisite> findByTopicId(Long topicId);

    List<TopicPrerequisite> findByPrerequisiteTopicId(Long prerequisiteTopicId);
}
