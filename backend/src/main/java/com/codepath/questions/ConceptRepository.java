package com.codepath.questions;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptRepository extends JpaRepository<Concept, Long> {

    List<Concept> findByTopicIdOrderByNameAsc(Long topicId);

    Optional<Concept> findByTopicIdAndName(Long topicId, String name);
}
