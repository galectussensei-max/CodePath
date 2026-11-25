package com.codepath.questions;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    Optional<Topic> findByName(String name);

    List<Topic> findAllByOrderByDisplayOrderAsc();
}
