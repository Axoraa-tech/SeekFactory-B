package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Request.comment.CommentCreateRequest;
import seekfactory.axoraa.dto.Request.comment.ReplyCreateRequest;
import seekfactory.axoraa.dto.Response.comment.CommentResponse;
import seekfactory.axoraa.dto.Response.comment.CommentReplyResponse;

import java.util.List;

public interface CommentService {

    List<CommentResponse> listByReelId(String reelId);

    CommentResponse addComment(String reelId, String userId, CommentCreateRequest request);

    CommentReplyResponse addReply(String commentId, String userId, ReplyCreateRequest request);
}