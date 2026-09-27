package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Request.comment.CommentCreateRequest;
import seekfactory.axoraa.dto.Request.comment.ReplyCreateRequest;
import seekfactory.axoraa.dto.Response.comment.CommentResponse;
import seekfactory.axoraa.dto.Response.comment.CommentReplyResponse;

import java.util.List;
import java.util.Map;

public interface CommentService {

    /** Top-level comments with replies; viewerId (nullable) fills likedByMe. */
    List<CommentResponse> listByReelId(String reelId, String viewerId);

    /** Likes or unlikes a comment or reply. Returns { liked, likes }. */
    Map<String, Object> toggleLike(String commentId, String userId);

    CommentResponse addComment(String reelId, String userId, CommentCreateRequest request);

    CommentReplyResponse addReply(String commentId, String userId, ReplyCreateRequest request);
}