package com.codepath.assessment;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codepath.auth.UserPrincipal;
import com.codepath.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/assessment")
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;

    @GetMapping("/diagnostic")
    public ResponseEntity<ApiResponse<List<DiagnosticQuestionDto>>> getDiagnostic() {
        return ResponseEntity.ok(ApiResponse.success(assessmentService.getDiagnosticQuestions()));
    }

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<DiagnosticResultDto>> submit(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody DiagnosticSubmitRequest request) {
        DiagnosticResultDto result = assessmentService.submit(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Diagnostic graded", result));
    }
}
