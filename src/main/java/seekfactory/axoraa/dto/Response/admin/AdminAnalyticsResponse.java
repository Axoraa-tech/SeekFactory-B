package seekfactory.axoraa.dto.Response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Live platform analytics for the admin dashboard, computed for a rolling period.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAnalyticsResponse {

    private int periodDays;
    /** Bucket size of the time series: "day", "week" or "month". */
    private String bucket;
    private Instant generatedAt;

    private Totals totals;
    private List<Kpi> kpis;
    private List<SignupPoint> signups;
    private List<CountPoint> rfqsOverTime;
    private List<LabelCount> rfqsByStatus;
    private List<LabelCount> usersByCountry;
    private List<LabelCount> topRfqCategories;
    private List<Activity> recentActivity;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Totals {
        private long users;
        private long buyers;
        private long suppliers;
        private long admins;
        private long activeUsers;
        private long manufacturers;
        private long verifiedManufacturers;
        private long premiumManufacturers;
        private long products;
        private long activeProducts;
        private long reels;
        private long reelViews;
        private long rfqs;
        private long pendingRfqs;
        private long quotes;
        private long conversations;
        private long messages;
    }

    /** A headline metric: new items in the current period vs the previous period of equal length. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Kpi {
        private String key;
        private long current;
        private long previous;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignupPoint {
        private Instant bucketStart;
        private long buyers;
        private long suppliers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CountPoint {
        private Instant bucketStart;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LabelCount {
        private String label;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Activity {
        /** USER_REGISTERED, MANUFACTURER_JOINED, RFQ_CREATED, QUOTE_SUBMITTED, PRODUCT_ADDED */
        private String type;
        private String title;
        private String subtitle;
        private Instant occurredAt;
    }
}
