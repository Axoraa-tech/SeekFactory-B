package seekfactory.axoraa.dto.Response.factory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactoryStatsResponse {

    private long totalProductViews;
    private double productViewsChange;
    private long factoryProfileVisits;
    private double profileVisitsChange;
    private long videoSeekPlays;
    private double videoPlaysChange;
    private int activeRfqsCount;
    private int pendingRfqsCount;
    private double responseRatePercent;
    private double avgResponseTimeHours;
    private int followerCount;
    private int totalProductsCount;
    private int totalSeeksCount;
}