package com.codepath.questions;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codepath.common.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    /** Optional filters: topicId, conceptId, difficulty (EASY, MEDIUM or HARD). */
    @GetMapping
    public ResponseEntity<ApiResponse<List<QuestionSummaryDto>>> list(
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) Long conceptId,
            @RequestParam(required = false) Difficulty difficulty) {
        return ResponseEntity.ok(ApiResponse.success(questionService.list(topicId, conceptId, difficulty)));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<QuestionDetailDto>> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(questionService.getBySlug(slug)));
    }
}
