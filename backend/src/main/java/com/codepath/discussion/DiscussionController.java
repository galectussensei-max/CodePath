package com.codepath.discussion;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codepath.auth.UserPrincipal;
import com.codepath.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/discussions")
@RequiredArgsConstructor
public class DiscussionController {

    private final DiscussionService discussionService;

    @GetMapping("/question/{questionId}")
    public ResponseEntity<ApiResponse<List<DiscussionSummaryDto>>> list(@PathVariable Long questionId) {
        return ResponseEntity.ok(ApiResponse.success(discussionService.listForQuestion(questionId)));
    }

    @PostMapping("/question/{questionId}")
    public ResponseEntity<ApiResponse<DiscussionDetailDto>> create(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long questionId,
            @Valid @RequestBody CreateDiscussionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Discussion created",
                discussionService.create(principal.getId(), questionId, request)));
    }

    @GetMapping("/{discussionId}")
    public ResponseEntity<ApiResponse<DiscussionDetailDto>> get(@PathVariable Long discussionId) {
        return ResponseEntity.ok(ApiResponse.success(discussionService.get(discussionId)));
    }

    @PostMapping("/{discussionId}/comments")
    public ResponseEntity<ApiResponse<CommentDto>> comment(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long discussionId,
            @Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Comment added",
                discussionService.addComment(principal.getId(), discussionId, request)));
    }

    @PostMapping("/{discussionId}/upvote")
    public ResponseEntity<ApiResponse<DiscussionSummaryDto>> upvote(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long discussionId) {
        return ResponseEntity.ok(ApiResponse.success("Upvoted", discussionService.upvote(principal.getId(), discussionId)));
    }
}
