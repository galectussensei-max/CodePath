package com.codepath.learning;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicMasteryRepository extends JpaRepository<TopicMastery, Long> {

    List<TopicMastery> findByUserId(Long userId);

    Optional<TopicMastery> findByUserIdAndTopicId(Long userId, Long topicId);

    List<TopicMastery> findByUserIdAndNextRevisionDueBefore(Long userId, Instant cutoff);
}
