package com.codepath.roadmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.codepath.TestData;
import com.codepath.learning.MasteryStatus;
import com.codepath.learning.TopicMastery;
import com.codepath.learning.TopicMasteryRepository;
import com.codepath.questions.Difficulty;
import com.codepath.questions.QuestionRepository;
import com.codepath.questions.TopicPrerequisiteRepository;
import com.codepath.questions.TopicRepository;
import com.codepath.submissions.SubmissionRepository;
import com.codepath.submissions.Verdict;
import com.codepath.users.LearnerProfile;
import com.codepath.users.LearnerProfileRepository;
import com.codepath.users.User;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoadmapServiceTest {

    private static final Long USER_ID = 4L;

    @Mock
    private TopicRepository topicRepository;
    @Mock
    private TopicPrerequisiteRepository topicPrerequisiteRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private TopicMasteryRepository topicMasteryRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private LearnerProfileRepository learnerProfileRepository;

    private final TestData data = new TestData();
    private final User user = TestData.user(USER_ID);
    private RoadmapService service;

    @BeforeEach
    void setUp() {
        service = new RoadmapService(topicRepository, topicPrerequisiteRepository, questionRepository,
                topicMasteryRepository, submissionRepository, learnerProfileRepository);
        when(topicRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(data.topics());
        when(topicPrerequisiteRepository.findAll()).thenReturn(data.links());
        when(questionRepository.findPracticeQuestions(anyString())).thenReturn(data.questions());
        when(topicMasteryRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(submissionRepository.findQuestionIdsByVerdict(USER_ID, Verdict.ACCEPTED)).thenReturn(List.of());
        when(learnerProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
    }

    private void givenProfile(int dailyMinutes) {
        LearnerProfile profile = new LearnerProfile();
        profile.setUser(user);
        profile.setPrepGoal("Placements");
        profile.setTargetLevel("Intermediate");
        profile.setDailyStudyMinutes(dailyMinutes);
        when(learnerProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
    }

    @Test
    void freshLearnerSeesOnlyTheRootTopicOpenAndTheRestLockedWithReasons() {
        RoadmapDto roadmap = service.build(USER_ID);

        List<RoadmapDto.Step> steps = roadmap.steps();
        assertEquals(List.of("Arrays", "Strings", "Dynamic Programming"),
                steps.stream().map(RoadmapDto.Step::topic).toList());
        assertEquals(List.of(1, 2, 3), steps.stream().map(RoadmapDto.Step::order).toList());
        assertEquals(RoadmapStepState.AVAILABLE, steps.get(0).state());
        assertEquals(RoadmapStepState.LOCKED, steps.get(1).state());
        assertEquals(RoadmapStepState.LOCKED, steps.get(2).state());
        assertNull(steps.get(0).lockedReason());
        assertEquals("Recommended after you reach 40% in Arrays", steps.get(1).lockedReason());
        assertEquals("Recommended after you reach 40% in Arrays, Strings", steps.get(2).lockedReason());
        assertNull(roadmap.estimatedDaysRemaining(), "no profile means no pacing estimate");
        assertTrue(roadmap.note().contains("optional"));
    }

    @Test
    void problemsAreOrderedEasyToHardAndFlagSolved() {
        when(submissionRepository.findQuestionIdsByVerdict(USER_ID, Verdict.ACCEPTED))
                .thenReturn(List.of(data.a4.getId()));

        RoadmapDto.Step arrays = service.build(USER_ID).steps().get(0);

        assertEquals(List.of(Difficulty.EASY, Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD),
                arrays.problems().stream().map(RoadmapDto.Problem::difficulty).toList());
        assertEquals(1, arrays.solvedCount());
        assertEquals(4, arrays.totalProblems());
        assertTrue(arrays.problems().stream().filter(p -> p.slug().equals("arrays-easy-sw")).findFirst()
                .orElseThrow().solved());
        assertFalse(arrays.problems().get(0).solved());
        assertEquals(RoadmapStepState.IN_PROGRESS, arrays.state());
    }

    @Test
    void metPrerequisitesUnlockTheNextTopicAndShowTheirProgress() {
        when(topicMasteryRepository.findByUserId(USER_ID)).thenReturn(List.of(
                TestData.mastery(user, data.arrays, 75, MasteryStatus.STRONG, null),
                TestData.mastery(user, data.strings, 55, MasteryStatus.DEVELOPING, null)));

        List<RoadmapDto.Step> steps = service.build(USER_ID).steps();

        assertEquals(RoadmapStepState.IN_PROGRESS, steps.get(0).state());
        assertEquals(RoadmapStepState.IN_PROGRESS, steps.get(1).state());
        assertEquals(RoadmapStepState.AVAILABLE, steps.get(2).state());
        RoadmapDto.Step dp = steps.get(2);
        assertEquals(2, dp.prerequisites().size());
        assertTrue(dp.prerequisites().stream().allMatch(RoadmapDto.Prerequisite::met));
        assertEquals(75.0, steps.get(0).masteryScore());
        assertEquals(MasteryStatus.UNASSESSED, dp.masteryStatus());
    }

    @Test
    void estimatesDaysFromDailyStudyMinutesAndExpectedSolveTimes() {
        givenProfile(100);

        RoadmapDto roadmap = service.build(USER_ID);

        // 4 Easy x 15 + 3 Medium x 30 + 2 Hard x 45 = 240 minutes; 240 / 100 rounded up = 3 days.
        assertEquals(240, roadmap.steps().stream().mapToInt(RoadmapDto.Step::estimatedMinutesRemaining).sum());
        assertEquals(3, roadmap.estimatedDaysRemaining());
        assertEquals("Placements", roadmap.goal().prepGoal());
        assertEquals(100, roadmap.goal().dailyStudyMinutes());
    }

    @Test
    void completedStateRequiresEveryProblemSolvedEvenIfLocked() {
        assertEquals(RoadmapStepState.COMPLETED, RoadmapService.stateOf(false, 2, 2, null));
        assertEquals(RoadmapStepState.LOCKED, RoadmapService.stateOf(false, 1, 2, null));
        assertEquals(RoadmapStepState.AVAILABLE, RoadmapService.stateOf(true, 0, 2, null));
        TopicMastery attempted = TestData.mastery(user, data.arrays, 10, MasteryStatus.WEAK, null);
        assertEquals(RoadmapStepState.IN_PROGRESS, RoadmapService.stateOf(true, 0, 2, attempted));
        assertEquals(RoadmapStepState.AVAILABLE, RoadmapService.stateOf(true, 0, 0, null));
    }
}
