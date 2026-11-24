package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.admin.AdminAnalyticsResponse;
import seekfactory.axoraa.dto.Response.admin.AdminDashboardStatsResponse;

public interface AdminDashboardService {
    AdminDashboardStatsResponse getDashboardStats();

    AdminAnalyticsResponse getAnalytics(int days);

    /** Prepares the default dashboard in the background so it is ready when the admin lands on it. */
    default void warmAnalytics() {
    }
}
