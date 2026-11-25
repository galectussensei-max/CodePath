package com.codepath.questions;

import com.codepath.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "concepts",
        uniqueConstraints = @UniqueConstraint(name = "uk_concepts_topic_name", columnNames = {"topic_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class Concept extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "name", nullable = false, length = 160)
    private String name;
}
