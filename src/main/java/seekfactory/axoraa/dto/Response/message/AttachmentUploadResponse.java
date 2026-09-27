package seekfactory.axoraa.dto.Response.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of uploading a chat attachment; pass these fields back when sending the message. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentUploadResponse {
    /** Participant-only URL: /api/v1/conversations/{conversationId}/attachments/{key}. */
    private String url;
    private String name;
    private String size;
    private String contentType;
}
