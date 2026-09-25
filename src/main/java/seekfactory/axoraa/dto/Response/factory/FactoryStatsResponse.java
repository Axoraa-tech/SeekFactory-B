package seekfactory.axoraa.dto.Response.factory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Seller dashboard KPIs, all computed from real data (see FactoryServiceImpl#getStats).
 * Nullable (boxed) fields are omitted from JSON when there is not enough data yet,
 * e.g. no views in the previous period or no RFQs received.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactoryStatsResponse {

    /** Length of the reporting window for views; change % compares with the window before it. */
    private int periodDays;

    private long totalProductViews;
    private Double productViewsChange;
    /** Profile visits are not tracked yet; always 0 with a null change. */
    private long factoryProfileVisits;
    private Double profileVisitsChange;
    private long videoSeekPlays;
    private Double videoPlaysChange;

    /** Matched RFQs still open (SUBMITTED, REVIEWING, QUOTING, QUOTED). */
    private int activeRfqsCount;
    /** Active RFQs this factory has not quoted yet. */
    private int pendingRfqsCount;
    /** % of matched RFQs (received in the response window) that this factory quoted. */
    private Double responseRatePercent;
    /** Mean hours from RFQ submission to this factory's first quote. */
    private Double avgResponseTimeHours;
    private int responseWindowDays;

    private int followerCount;
    private int totalProductsCount;
    private int totalSeeksCount;
}
