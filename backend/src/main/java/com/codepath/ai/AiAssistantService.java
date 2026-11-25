package com.codepath.ai;

import org.springframework.stereotype.Service;

import com.codepath.common.ServiceUnavailableException;
import com.codepath.config.AiProperties;
import com.codepath.submissions.Verdict;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Safeguarded progressive AI (blueprint 5B). Builds the strict prompts, calls the LLM through
 * {@link LlmClient}, and refuses to return any reply that {@link AiResponseGuard} rejects. It knows nothing
 * about the database or the Judge0 module; the output is always unverified guidance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantService {

    private final LlmClient llmClient;
    private final AiProperties properties;

    /** One progressive hint (Level 1-4) for the learner's current attempt. */
    public AiGeneration generateHint(QuestionBrief question, String sourceCode, Verdict verdict, HintLevel level) {
        String system = AiPromptBuilder.hintSystemPrompt(level);
        String user = AiPromptBuilder.hintUserPrompt(question, sourceCode, verdict, level,
                properties.maxSourceChars());
        return generateGuarded(level.interactionType(), system, user);
    }

    /** Post-AC review of an already Accepted solution: complexity plus alternative approaches. */
    public AiGeneration generateReview(QuestionBrief question, String acceptedSourceCode) {
        String system = AiPromptBuilder.reviewSystemPrompt();
        String user = AiPromptBuilder.reviewUserPrompt(question, acceptedSourceCode, properties.maxSourceChars());
        return generateGuarded(InteractionType.AC_REVIEW, system, user);
    }

    /** Calls the model; on a guard violation retries once with a reminder, then gives up without a reply. */
    private AiGeneration generateGuarded(InteractionType type, String system, String user) {
        String reply = llmClient.complete(system, user);
        String violation = AiResponseGuard.violation(type, reply);
        if (violation == null) {
            return new AiGeneration(reply.strip(), user);
        }

        log.warn("AI reply for {} rejected by the guard: {}; retrying once", type, violation);
        String retryPrompt = user + AiPromptBuilder.retryReminder(violation);
        reply = llmClient.complete(system, retryPrompt);
        violation = AiResponseGuard.violation(type, reply);
        if (violation == null) {
            return new AiGeneration(reply.strip(), retryPrompt);
        }

        log.warn("AI reply for {} rejected again by the guard: {}", type, violation);
        throw new ServiceUnavailableException(
                "The AI assistant could not produce a safe answer this time; please try again");
    }
}
