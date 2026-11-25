package com.codepath.assessment;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.codepath.questions.Question;

/**
 * Turns a diagnostic {@link Question} into a client DTO. Options are the description lines
 * {@code A. ...} to {@code D. ...}; everything before the first option is the prompt.
 */
final class DiagnosticQuestionMapper {

    private static final Pattern OPTION_LINE = Pattern.compile("^([A-D])\\.\\s+(.+)$");
    private static final Pattern TRAILING_OPTIONS_HEADING = Pattern.compile("(?s)\\s*\\*\\*Options\\*\\*\\s*$");

    private DiagnosticQuestionMapper() {
    }

    static DiagnosticQuestionDto toDto(Question question) {
        List<String> promptLines = new ArrayList<>();
        List<DiagnosticQuestionDto.Option> options = new ArrayList<>();

        for (String line : question.getDescription().strip().split("\\R")) {
            Matcher matcher = OPTION_LINE.matcher(line.strip());
            if (matcher.matches()) {
                options.add(new DiagnosticQuestionDto.Option(matcher.group(1), matcher.group(2).strip()));
            } else if (options.isEmpty()) {
                promptLines.add(line);
            }
        }
        if (options.size() < 2) {
            throw new IllegalStateException(
                    "Diagnostic question '" + question.getSlug() + "' must list at least two options (A. ... D.)");
        }

        String prompt = TRAILING_OPTIONS_HEADING.matcher(String.join("\n", promptLines)).replaceFirst("").strip();
        return new DiagnosticQuestionDto(
                question.getId(),
                question.getTitle(),
                question.getTopic().getName(),
                question.getDifficulty().name(),
                prompt,
                List.copyOf(options));
    }
}
