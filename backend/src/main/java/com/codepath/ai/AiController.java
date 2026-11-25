package com.codepath.ai;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codepath.auth.UserPrincipal;
import com.codepath.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Unverified AI guidance. Every response carries {@code "isVerified": false}. */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiGuidanceService guidanceService;

    /** Progressive hint, Level 1 -> 4. Never returns a complete solution; counts toward the hint total. */
    @PostMapping("/hint")
    public ResponseEntity<ApiResponse<AiGuidanceDto>> hint(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody HintRequest request) {
        return ResponseEntity.ok(ApiResponse.success("AI hint (unverified guidance)",
                guidanceService.hint(principal.getId(), request)));
    }

    /** Complexity review and alternative approaches for the learner's own ACCEPTED submission. */
    @PostMapping("/review")
    public ResponseEntity<ApiResponse<AiGuidanceDto>> review(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("AI review (unverified guidance)",
                guidanceService.review(principal.getId(), request)));
    }
}
