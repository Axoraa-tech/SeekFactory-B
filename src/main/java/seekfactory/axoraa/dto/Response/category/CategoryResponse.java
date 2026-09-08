package seekfactory.axoraa.dto.Response.category;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {

    private String id;
    private String slug;
    private String name;
    private int listingCount;
    private String parentId;     // null for root categories
    private String icon;
}