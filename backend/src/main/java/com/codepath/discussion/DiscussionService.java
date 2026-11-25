package com.codepath.discussion;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codepath.common.ConflictException;
import com.codepath.common.ResourceNotFoundException;
import com.codepath.config.DataSeeder;
import com.codepath.questions.Question;
import com.codepath.questions.QuestionRepository;
import com.codepath.users.User;
import com.codepath.users.UserRepository;

import lombok.RequiredArgsConstructor;

/** Problem-specific threads, comments and upvotes. Diagnostic questions have no discussions. */
@Service
@RequiredArgsConstructor
public class DiscussionService {

    private final DiscussionRepository discussionRepository;
    private final DiscussionCommentRepository commentRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<DiscussionSummaryDto> listForQuestion(Long questionId) {
        Question question = findPracticeQuestion(questionId);
        List<Discussion> threads = discussionRepository.findThreads(question.getId());
        if (threads.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = commentRepository
                .countByDiscussionIds(threads.stream().map(Discussion::getId).toList()).stream()
                .collect(Collectors.toMap(CommentCount::discussionId, CommentCount::count, Long::sum));
        return threads.stream()
                .map(thread -> DiscussionSummaryDto.of(thread, counts.getOrDefault(thread.getId(), 0L)))
                .toList();
    }

    @Transactional
    public DiscussionDetailDto create(Long userId, Long questionId, CreateDiscussionRequest request) {
        Question question = findPracticeQuestion(questionId);
        User user = findUser(userId);
        Discussion discussion = new Discussion();
        discussion.setQuestion(question);
        discussion.setUser(user);
        discussion.setTitle(request.title().strip());
        discussion.setContent(request.content().strip());
        discussion.setUpvotes(0);
        Discussion saved = discussionRepository.save(discussion);
        return DiscussionDetailDto.of(saved, List.of());
    }

    @Transactional(readOnly = true)
    public DiscussionDetailDto get(Long discussionId) {
        Discussion discussion = findDiscussion(discussionId);
        return DiscussionDetailDto.of(discussion, commentRepository.findThreadComments(discussionId));
    }

    @Transactional
    public CommentDto addComment(Long userId, Long discussionId, CreateCommentRequest request) {
        Discussion discussion = findDiscussion(discussionId);
        User user = findUser(userId);
        DiscussionComment comment = new DiscussionComment();
        comment.setDiscussion(discussion);
        comment.setUser(user);
        comment.setContent(request.content().strip());
        return CommentDto.from(commentRepository.save(comment));
    }

    /**
     * Adds one upvote. The schema has only a counter column (no per-user vote table), so a learner cannot
     * upvote their own thread but repeat upvotes by others are not de-duplicated.
     */
    @Transactional
    public DiscussionSummaryDto upvote(Long userId, Long discussionId) {
        Discussion discussion = findDiscussion(discussionId);
        if (discussion.getUser().getId().equals(userId)) {
            throw new ConflictException("You cannot upvote your own discussion");
        }
        discussionRepository.incrementUpvotes(discussionId);
        Discussion refreshed = findDiscussion(discussionId);
        long commentCount = commentRepository.countByDiscussionIds(List.of(discussionId)).stream()
                .map(CommentCount::count).findFirst().orElse(0L);
        return DiscussionSummaryDto.of(refreshed, commentCount);
    }

    private Discussion findDiscussion(Long discussionId) {
        return discussionRepository.findDetailById(discussionId)
                .filter(found -> !DataSeeder.DIAGNOSTIC_TAG.equals(found.getQuestion().getPlacementTags()))
                .orElseThrow(() -> ResourceNotFoundException.of("Discussion", discussionId));
    }

    private Question findPracticeQuestion(Long questionId) {
        return questionRepository.findById(questionId)
                .filter(question -> !DataSeeder.DIAGNOSTIC_TAG.equals(question.getPlacementTags()))
                .orElseThrow(() -> ResourceNotFoundException.of("Question", questionId));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }
}
