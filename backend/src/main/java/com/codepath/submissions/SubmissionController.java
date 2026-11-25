package com.codepath.submissions;

import java.util.List;

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
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    /** Verified Judge0 run against the public sample cases. Does not save anything or affect mastery. */
    @PostMapping("/run")
    public ResponseEntity<ApiResponse<SubmissionResultDto>> run(@Valid @RequestBody RunRequest request) {
        return ResponseEntity.ok(ApiResponse.success(submissionService.run(request)));
    }

    /** Verified Judge0 run against public and hidden cases; saves the submission and updates mastery. */
    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<SubmissionResultDto>> submit(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody SubmitRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Submission judged", submissionService.submit(principal.getId(), request)));
    }

    @GetMapping("/question/{questionId}")
    public ResponseEntity<ApiResponse<List<SubmissionHistoryDto>>> history(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long questionId) {
        return ResponseEntity.ok(ApiResponse.success(submissionService.history(principal.getId(), questionId)));
    }
}
