package com.codepath.revision;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codepath.auth.UserPrincipal;
import com.codepath.common.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/revision")
@RequiredArgsConstructor
public class RevisionController {

    private final RevisionService revisionService;

    @GetMapping("/queue")
    public ResponseEntity<ApiResponse<RevisionQueueDto>> queue(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(revisionService.queue(principal.getId())));
    }
}
