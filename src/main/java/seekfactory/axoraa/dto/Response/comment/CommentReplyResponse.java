package seekfactory.axoraa.dto.Response.comment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentReplyResponse {

    private String id;
    private String authorName;
    private String authorAvatarUrl;
    private String authorCompany;
    private String authorCountry;
    private boolean isVerified;
    private String content;
    private String createdAt;
    private int likes;
}