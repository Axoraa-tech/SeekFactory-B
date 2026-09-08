package seekfactory.axoraa.dto.Response.reel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReelResponse {

    private String id;
    private String manufacturerId;
    private String title;
    private String description;
    private List<String> hashtags;
    private String posterUrl;
    private String videoUrl;
    private int durationSec;
    private int startSec;
    private long views;
    private int likes;
    private int comments;
    private int shares;
    private int saves;
    private String tab;          // "for-you" or "following"
    private List<String> productIds;
}