package com.codepath.discussion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.codepath.TestData;
import com.codepath.common.ConflictException;
import com.codepath.common.ResourceNotFoundException;
import com.codepath.config.DataSeeder;
import com.codepath.questions.Question;
import com.codepath.questions.QuestionRepository;
import com.codepath.users.User;
import com.codepath.users.UserRepository;

class DiscussionServiceTest {

    private DiscussionRepository discussionRepository;
    private DiscussionCommentRepository commentRepository;
    private QuestionRepository questionRepository;
    private UserRepository userRepository;
    private DiscussionService service;

    private final TestData data = new TestData();
    private final User alice = named(7L, "Alice");
    private final User bob = named(8L, "Bob");

    private static User named(Long id, String name) {
        User user = TestData.user(id);
        user.setName(name);
        user.setEmail(name.toLowerCase() + "@example.com");
        return user;
    }

    @BeforeEach
    void setUp() {
        discussionRepository = mock(DiscussionRepository.class);
        commentRepository = mock(DiscussionCommentRepository.class);
        questionRepository = mock(QuestionRepository.class);
        userRepository = mock(UserRepository.class);
        service = new DiscussionService(discussionRepository, commentRepository, questionRepository,
                userRepository);
        when(questionRepository.findById(1L)).thenReturn(Optional.of(data.a1));
        when(userRepository.findById(7L)).thenReturn(Optional.of(alice));
        when(userRepository.findById(8L)).thenReturn(Optional.of(bob));
    }

    private Discussion thread(Long id, User author, int upvotes) {
        Discussion discussion = new Discussion();
        discussion.setId(id);
        discussion.setQuestion(data.a1);
        discussion.setUser(author);
        discussion.setTitle("Title " + id);
        discussion.setContent("Body " + id);
        discussion.setUpvotes(upvotes);
        discussion.setCreatedAt(Instant.parse("2026-11-23T10:00:00Z"));
        return discussion;
    }

    @Test
    void listReturnsThreadsWithCommentCountsAndAuthorNamesOnly() {
        Discussion first = thread(10L, alice, 3);
        Discussion second = thread(11L, bob, 0);
        when(discussionRepository.findThreads(1L)).thenReturn(List.of(first, second));
        when(commentRepository.countByDiscussionIds(List.of(10L, 11L))).thenReturn(List.of(new CommentCount(10L, 4L)));

        List<DiscussionSummaryDto> result = service.listForQuestion(1L);

        assertEquals(2, result.size());
        assertEquals(4L, result.get(0).commentCount());
        assertEquals(0L, result.get(1).commentCount());
        assertEquals("Alice", result.get(0).authorName());
        assertEquals(3, result.get(0).upvotes());
        assertTrue(!result.toString().contains("@example.com"));
    }

    @Test
    void listForAQuestionWithoutThreadsIsEmptyAndSkipsTheCountQuery() {
        when(discussionRepository.findThreads(1L)).thenReturn(List.of());

        assertTrue(service.listForQuestion(1L).isEmpty());
        verify(commentRepository, never()).countByDiscussionIds(any());
    }

    @Test
    void diagnosticAndUnknownQuestionsHaveNoDiscussions() {
        Question diagnostic = new Question();
        diagnostic.setId(99L);
        diagnostic.setPlacementTags(DataSeeder.DIAGNOSTIC_TAG);
        when(questionRepository.findById(99L)).thenReturn(Optional.of(diagnostic));

        assertThrows(ResourceNotFoundException.class, () -> service.listForQuestion(99L));
        assertThrows(ResourceNotFoundException.class, () -> service.listForQuestion(12345L));
        assertThrows(ResourceNotFoundException.class,
                () -> service.create(7L, 99L, new CreateDiscussionRequest("Title", "Body")));
    }

    @Test
    void createTrimsTextAndStartsWithZeroUpvotes() {
        when(discussionRepository.save(any(Discussion.class))).thenAnswer(invocation -> {
            Discussion saved = invocation.getArgument(0);
            saved.setId(20L);
            saved.setCreatedAt(Instant.now());
            return saved;
        });

        DiscussionDetailDto dto = service.create(7L, 1L, new CreateDiscussionRequest("  Hello  ", "  World  "));

        ArgumentCaptor<Discussion> captor = ArgumentCaptor.forClass(Discussion.class);
        verify(discussionRepository).save(captor.capture());
        assertEquals("Hello", captor.getValue().getTitle());
        assertEquals("World", captor.getValue().getContent());
        assertEquals(0, captor.getValue().getUpvotes());
        assertEquals(20L, dto.id());
        assertEquals("Alice", dto.authorName());
        assertTrue(dto.comments().isEmpty());
    }

    @Test
    void getReturnsTheThreadWithItsComments() {
        Discussion discussion = thread(10L, alice, 1);
        DiscussionComment comment = new DiscussionComment();
        comment.setId(30L);
        comment.setDiscussion(discussion);
        comment.setUser(bob);
        comment.setContent("Nice");
        comment.setCreatedAt(Instant.now());
        when(discussionRepository.findDetailById(10L)).thenReturn(Optional.of(discussion));
        when(commentRepository.findThreadComments(10L)).thenReturn(List.of(comment));

        DiscussionDetailDto dto = service.get(10L);

        assertEquals(1, dto.comments().size());
        assertEquals("Bob", dto.comments().get(0).authorName());
        assertEquals("Nice", dto.comments().get(0).content());
    }

    @Test
    void getOfAMissingThreadIsNotFound() {
        when(discussionRepository.findDetailById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(404L));
    }

    @Test
    void addCommentSavesATrimmedCommentByTheCaller() {
        when(discussionRepository.findDetailById(10L)).thenReturn(Optional.of(thread(10L, alice, 0)));
        when(commentRepository.save(any(DiscussionComment.class))).thenAnswer(invocation -> {
            DiscussionComment saved = invocation.getArgument(0);
            saved.setId(31L);
            saved.setCreatedAt(Instant.now());
            return saved;
        });

        CommentDto dto = service.addComment(8L, 10L, new CreateCommentRequest("  Thanks  "));

        assertEquals("Thanks", dto.content());
        assertEquals("Bob", dto.authorName());
    }

    @Test
    void upvoteIncrementsAtomicallyAndReturnsTheNewCount() {
        Discussion before = thread(10L, alice, 2);
        Discussion after = thread(10L, alice, 3);
        when(discussionRepository.findDetailById(10L)).thenReturn(Optional.of(before), Optional.of(after));
        when(commentRepository.countByDiscussionIds(List.of(10L))).thenReturn(List.of(new CommentCount(10L, 5L)));

        DiscussionSummaryDto dto = service.upvote(8L, 10L);

        verify(discussionRepository).incrementUpvotes(10L);
        assertEquals(3, dto.upvotes());
        assertEquals(5L, dto.commentCount());
    }

    @Test
    void cannotUpvoteYourOwnThread() {
        when(discussionRepository.findDetailById(10L)).thenReturn(Optional.of(thread(10L, alice, 2)));

        assertThrows(ConflictException.class, () -> service.upvote(7L, 10L));
        verify(discussionRepository, never()).incrementUpvotes(any());
    }
}
