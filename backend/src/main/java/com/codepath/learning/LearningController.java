package com.codepath.learning;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codepath.auth.UserPrincipal;
import com.codepath.common.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/learning")
@RequiredArgsConstructor
public class LearningController {

    private final RecommendationService recommendationService;

    /** Ranked next problems, each with a plain-language {@code reason}; limit defaults to 5 (max 20). */
    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<RecommendationsDto>> recommendations(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(ApiResponse.success(
                recommendationService.recommend(principal.getId(), limit)));
    }
}
