package com.codepath.questions;

import java.util.ArrayList;
import java.util.List;

import com.codepath.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "topics", uniqueConstraints = @UniqueConstraint(name = "uk_topics_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
public class Topic extends BaseEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @OneToMany(mappedBy = "topic")
    private List<Concept> concepts = new ArrayList<>();

    @OneToMany(mappedBy = "topic")
    private List<Question> questions = new ArrayList<>();
}
