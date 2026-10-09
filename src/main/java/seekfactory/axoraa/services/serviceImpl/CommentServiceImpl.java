package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Request.comment.CommentCreateRequest;
import seekfactory.axoraa.dto.Request.comment.ReplyCreateRequest;
import seekfactory.axoraa.dto.Response.comment.CommentReplyResponse;
import seekfactory.axoraa.dto.Response.comment.CommentResponse;
import seekfactory.axoraa.entity.Comments.Comment;
import seekfactory.axoraa.entity.Comments.CommentLike;
import seekfactory.axoraa.entity.Reels.Reel;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.Comments.CommentLikeRepository;
import seekfactory.axoraa.repository.Comments.CommentRepository;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.CommentService;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages reel comments and threaded replies.
 *
 * Comment author metadata is denormalized at write time (authorName, authorAvatarUrl,
 * authorCompany, authorCountry) to avoid expensive joins during feed rendering.
 * This is a common pattern in high-read, low-write comment systems.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final ReelRepository reelRepository;
    private final UserRepository userRepository;
    private final CommentLikeRepository commentLikeRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> listByReelId(String reelId, String viewerId) {
        // Fetch only top-level comments (parent is null)
        // Replies are loaded via the @OneToMany relationship on Comment entity
        List<Comment> comments = commentRepository
                .findByReelIdAndParentIsNullOrderByCreatedAtDesc(reelId);

        List<CommentResponse> responses = comments.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        if (viewerId != null) {
            Set<String> liked = new HashSet<>(commentLikeRepository.findLikedCommentIdsOnReel(viewerId, reelId));
            for (CommentResponse c : responses) {
                c.setLikedByMe(liked.contains(c.getId()));
                c.getReplies().forEach(r -> r.setLikedByMe(liked.contains(r.getId())));
            }
        }
        return responses;
    }

    @Override
    public Map<String, Object> toggleLike(String commentId, String userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId));

        Optional<CommentLike> existing = commentLikeRepository.findByCommentIdAndUserId(commentId, userId);
        boolean liked;
        if (existing.isPresent()) {
            commentLikeRepository.delete(existing.get());
            comment.setLikesCount(Math.max(0, comment.getLikesCount() - 1));
            liked = false;
        } else {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
            commentLikeRepository.save(CommentLike.builder().comment(comment).user(user).build());
            comment.setLikesCount(comment.getLikesCount() + 1);
            liked = true;
        }
        commentRepository.save(comment);
        return Map.of("liked", liked, "likes", comment.getLikesCount());
    }

    @Override
    public CommentResponse addComment(String reelId, String userId, CommentCreateRequest request) {
        Reel reel = reelRepository.findById(reelId)
                .orElseThrow(() -> new ResourceNotFoundException("Reel", "id", reelId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Comment comment = Comment.builder()
                .reel(reel)
                .user(user)
                .authorName(user.getName())
                .authorAvatarUrl(user.getAvatarUrl())
                .authorCompany(user.getCompanyName())
                .authorCountry(user.getCountry())
                .isVerified(false)
                .content(request.getContent())
                .build();

        Comment saved = commentRepository.save(comment);

        // Increment reel comment count
        reel.setCommentsCount(reel.getCommentsCount() + 1);
        reelRepository.save(reel);

        log.info("Comment added to reel {} by user {}", reelId, userId);
        return mapToResponse(saved);
    }

    @Override
    public CommentReplyResponse addReply(String commentId, String userId, ReplyCreateRequest request) {
        Comment parentComment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId));
        // Threads are one level deep: a reply to a reply joins the top-level comment's thread
        // (otherwise it is saved but never shown, as only top-level comments list their replies)
        if (parentComment.getParent() != null) {
            parentComment = parentComment.getParent();
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Comment reply = Comment.builder()
                .reel(parentComment.getReel())
                .parent(parentComment)
                .user(user)
                .authorName(user.getName())
                .authorAvatarUrl(user.getAvatarUrl())
                .authorCompany(user.getCompanyName())
                .authorCountry(user.getCountry())
                .isVerified(false)
                .content(request.getContent())
                .build();

        Comment saved = commentRepository.save(reply);
        log.info("Reply added to comment {} by user {}", commentId, userId);

        return mapToReplyResponse(saved);
    }

    // ─── Private Helpers ──────────────────────────────────────

    private CommentResponse mapToResponse(Comment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .reelId(comment.getReel().getId())
                .authorName(comment.getAuthorName())
                .authorAvatarUrl(comment.getAuthorAvatarUrl())
                .authorCompany(comment.getAuthorCompany())
                .authorCountry(comment.getAuthorCountry())
                .isVerified(comment.getIsVerified())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt().toString())
                .likes(comment.getLikesCount())
                .replies(comment.getReplies().stream()
                        .map(this::mapToReplyResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    private CommentReplyResponse mapToReplyResponse(Comment reply) {
        return CommentReplyResponse.builder()
                .id(reply.getId())
                .authorName(reply.getAuthorName())
                .authorAvatarUrl(reply.getAuthorAvatarUrl())
                .authorCompany(reply.getAuthorCompany())
                .authorCountry(reply.getAuthorCountry())
                .isVerified(reply.getIsVerified())
                .content(reply.getContent())
                .createdAt(reply.getCreatedAt().toString())
                .likes(reply.getLikesCount())
                .build();
    }
}