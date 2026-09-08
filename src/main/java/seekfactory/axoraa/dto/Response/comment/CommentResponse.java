package seekfactory.axoraa.dto.Response.comment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {

    private String id;
    private String reelId;
    private String authorName;
    private String authorAvatarUrl;
    private String authorCompany;
    private String authorCountry;
    private boolean isVerified;
    private String content;
    private String createdAt;
    private int likes;
    private List<CommentReplyResponse> replies;
}