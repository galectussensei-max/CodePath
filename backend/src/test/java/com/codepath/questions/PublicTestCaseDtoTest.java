package com.codepath.questions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.codepath.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

class PublicTestCaseDtoTest {

    private static TestCase testCase(long id, String input, String output, boolean hidden) {
        TestCase tc = new TestCase();
        tc.setId(id);
        tc.setInputData(input);
        tc.setExpectedOutput(output);
        tc.setHidden(hidden);
        return tc;
    }

    @Test
    void mapsPublicCase() {
        PublicTestCaseDto dto = PublicTestCaseDto.from(testCase(1, "3 4", "7", false));

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.input()).isEqualTo("3 4");
        assertThat(dto.expectedOutput()).isEqualTo("7");
    }

    @Test
    void refusesToMapHiddenCase() {
        assertThatThrownBy(() -> PublicTestCaseDto.from(testCase(2, "secret-in", "secret-out", true)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicOnlyDropsHiddenCases() {
        List<PublicTestCaseDto> result = PublicTestCaseDto.publicOnly(List.of(
                testCase(1, "1 2", "3", false),
                testCase(2, "secret-in", "secret-out", true)));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    void serializedApiResponseNeverContainsHiddenDataOrFlag() throws Exception {
        List<PublicTestCaseDto> dtos = PublicTestCaseDto.publicOnly(List.of(
                testCase(1, "1 2", "3", false),
                testCase(2, "secret-in", "secret-out", true)));

        String json = new ObjectMapper().writeValueAsString(ApiResponse.success(dtos));

        assertThat(json).contains("\"success\":true");
        assertThat(json).doesNotContain("secret-in", "secret-out", "hidden", "Hidden");
    }
}
