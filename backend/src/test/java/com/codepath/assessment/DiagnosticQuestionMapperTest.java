package com.codepath.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.codepath.questions.Difficulty;
import com.codepath.questions.Question;
import com.codepath.questions.Topic;

class DiagnosticQuestionMapperTest {

    static final String DESCRIPTION = """
            **Diagnostic question (multiple choice)**

            Which technique finds a pair in a sorted array?

            **Options**

            A. Nested loops
            B. Hash set
            C. Two pointers
            D. Sort again

            Answer with a single letter: A, B, C or D.
            """;

    static Question question(String description) {
        Topic topic = new Topic();
        topic.setId(1L);
        topic.setName("Arrays");
        Question question = new Question();
        question.setId(5L);
        question.setSlug("diag-test");
        question.setTitle("Diagnostic: test");
        question.setTopic(topic);
        question.setDifficulty(Difficulty.EASY);
        question.setDescription(description);
        return question;
    }

    @Test
    void splitsPromptFromOptions() {
        DiagnosticQuestionDto dto = DiagnosticQuestionMapper.toDto(question(DESCRIPTION));

        assertEquals(5L, dto.id());
        assertEquals("Arrays", dto.topic());
        assertEquals("EASY", dto.difficulty());
        assertEquals(4, dto.options().size());
        assertEquals("C", dto.options().get(2).label());
        assertEquals("Two pointers", dto.options().get(2).text());
        assertTrue(dto.prompt().contains("Which technique finds a pair"));
        assertFalse(dto.prompt().contains("**Options**"));
        assertFalse(dto.prompt().contains("Answer with a single letter"));
        assertFalse(dto.prompt().contains("Nested loops"));
    }

    @Test
    void questionWithoutOptionsIsADataError() {
        assertThrows(IllegalStateException.class,
                () -> DiagnosticQuestionMapper.toDto(question("No options here")));
    }

    @Test
    void clientDtoHasNoAnswerKeyFields() {
        for (Class<?> type : new Class<?>[] {DiagnosticQuestionDto.class, DiagnosticQuestionDto.Option.class,
                DiagnosticResultDto.class, DiagnosticResultDto.TopicResult.class}) {
            Arrays.stream(type.getRecordComponents()).forEach(component -> {
                String name = component.getName().toLowerCase(Locale.ROOT);
                assertFalse(name.contains("expected") || name.contains("hidden") || name.contains("correctoption")
                        || name.equals("answer"), type.getSimpleName() + " exposes " + name);
            });
        }
    }
}
