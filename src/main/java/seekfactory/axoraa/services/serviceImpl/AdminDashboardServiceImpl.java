package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Response.admin.AdminAnalyticsResponse;
import seekfactory.axoraa.dto.Response.admin.AdminAnalyticsResponse.Activity;
import seekfactory.axoraa.dto.Response.admin.AdminAnalyticsResponse.CountPoint;
import seekfactory.axoraa.dto.Response.admin.AdminAnalyticsResponse.Kpi;
import seekfactory.axoraa.dto.Response.admin.AdminAnalyticsResponse.LabelCount;
import seekfactory.axoraa.dto.Response.admin.AdminAnalyticsResponse.SignupPoint;
import seekfactory.axoraa.dto.Response.admin.AdminAnalyticsResponse.Totals;
import seekfactory.axoraa.dto.Response.admin.AdminDashboardStatsResponse;
import seekfactory.axoraa.enums.RfqStatus;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.Rfqs.RfqRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.AdminDashboardService;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final Set<Integer> ALLOWED_PERIODS = Set.of(7, 30, 90, 365);
    private static final int TOP_COUNTRIES = 7;

    private final UserRepository userRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final RfqRepository rfqRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public AdminDashboardStatsResponse getDashboardStats() {
        long totalUsers = userRepository.count();
        long verifiedFactories = manufacturerRepository.countByVerifiedTrue();
        long pendingRfqs = rfqRepository.countByStatus(RfqStatus.SUBMITTED);

        return AdminDashboardStatsResponse.builder()
                .totalUsers(totalUsers)
                .verifiedFactories(verifiedFactories)
                .pendingRfqs(pendingRfqs)
                .build();
    }

    @Override
    public AdminAnalyticsResponse getAnalytics(int days) {
        int period = ALLOWED_PERIODS.contains(days) ? days : 30;
        // Whitelisted, never user-supplied text, so it is safe to inline into date_trunc / interval
        String bucket = period <= 31 ? "day" : period <= 90 ? "week" : "month";

        return AdminAnalyticsResponse.builder()
                .periodDays(period)
                .bucket(bucket)
                .generatedAt(Instant.now())
                .totals(loadTotals())
                .kpis(loadKpis(period))
                .signups(loadSignups(period, bucket))
                .rfqsOverTime(loadRfqsOverTime(period, bucket))
                .rfqsByStatus(loadRfqsByStatus())
                .usersByCountry(loadUsersByCountry())
                .topRfqCategories(loadTopRfqCategories())
                .recentActivity(loadRecentActivity())
                .build();
    }

    private Totals loadTotals() {
        String sql = """
                SELECT
                  (SELECT count(*) FROM users)                                    AS users,
                  (SELECT count(*) FROM users WHERE role = 'ROLE_BUYER')          AS buyers,
                  (SELECT count(*) FROM users WHERE role = 'ROLE_SUPPLIER')       AS suppliers,
                  (SELECT count(*) FROM users WHERE role = 'ROLE_ADMIN')          AS admins,
                  (SELECT count(*) FROM users WHERE is_active)                    AS active_users,
                  (SELECT count(*) FROM manufacturers)                            AS manufacturers,
                  (SELECT count(*) FROM manufacturers WHERE verified)             AS verified_manufacturers,
                  (SELECT count(*) FROM manufacturers WHERE premium)              AS premium_manufacturers,
                  (SELECT count(*) FROM products)                                 AS products,
                  (SELECT count(*) FROM products WHERE is_active)                 AS active_products,
                  (SELECT count(*) FROM reels)                                    AS reels,
                  (SELECT coalesce(sum(views_count), 0) FROM reels)               AS reel_views,
                  (SELECT count(*) FROM rfqs)                                     AS rfqs,
                  (SELECT count(*) FROM rfqs WHERE status = 'SUBMITTED')          AS pending_rfqs,
                  (SELECT count(*) FROM rfq_quotes)                               AS quotes,
                  (SELECT count(*) FROM conversations)                            AS conversations,
                  (SELECT count(*) FROM messages)                                 AS messages
                """;
        return jdbcTemplate.queryForObject(sql, (rs, i) -> Totals.builder()
                .users(rs.getLong("users"))
                .buyers(rs.getLong("buyers"))
                .suppliers(rs.getLong("suppliers"))
                .admins(rs.getLong("admins"))
                .activeUsers(rs.getLong("active_users"))
                .manufacturers(rs.getLong("manufacturers"))
                .verifiedManufacturers(rs.getLong("verified_manufacturers"))
                .premiumManufacturers(rs.getLong("premium_manufacturers"))
                .products(rs.getLong("products"))
                .activeProducts(rs.getLong("active_products"))
                .reels(rs.getLong("reels"))
                .reelViews(rs.getLong("reel_views"))
                .rfqs(rs.getLong("rfqs"))
                .pendingRfqs(rs.getLong("pending_rfqs"))
                .quotes(rs.getLong("quotes"))
                .conversations(rs.getLong("conversations"))
                .messages(rs.getLong("messages"))
                .build());
    }

    /** New records per table in the current period vs the previous period of equal length. */
    private List<Kpi> loadKpis(int period) {
        Map<String, String> sources = new java.util.LinkedHashMap<>();
        sources.put("buyers", "users WHERE role = 'ROLE_BUYER' AND");
        sources.put("suppliers", "users WHERE role = 'ROLE_SUPPLIER' AND");
        sources.put("manufacturers", "manufacturers WHERE");
        sources.put("products", "products WHERE");
        sources.put("rfqs", "rfqs WHERE");
        sources.put("quotes", "rfq_quotes WHERE");
        sources.put("messages", "messages WHERE");

        List<Kpi> kpis = new ArrayList<>();
        sources.forEach((key, from) -> {
            String sql = "SELECT"
                    + " count(*) FILTER (WHERE created_at >= now() - make_interval(days => ?)) AS cur,"
                    + " count(*) FILTER (WHERE created_at >= now() - make_interval(days => ?)"
                    + "                    AND created_at <  now() - make_interval(days => ?)) AS prev"
                    + " FROM " + from + " created_at >= now() - make_interval(days => ?)";
            kpis.add(jdbcTemplate.queryForObject(sql, (rs, i) -> Kpi.builder()
                    .key(key)
                    .current(rs.getLong("cur"))
                    .previous(rs.getLong("prev"))
                    .build(), period, period * 2, period, period * 2));
        });
        return kpis;
    }

    private List<SignupPoint> loadSignups(int period, String bucket) {
        String sql = seriesCte(bucket) + """
                SELECT b.s AS bucket_start,
                       count(u.id) FILTER (WHERE u.role = 'ROLE_BUYER')    AS buyers,
                       count(u.id) FILTER (WHERE u.role = 'ROLE_SUPPLIER') AS suppliers
                FROM b
                LEFT JOIN users u
                  ON date_trunc('%1$s', u.created_at) = b.s
                 AND u.created_at >= now() - make_interval(days => ?)
                GROUP BY b.s ORDER BY b.s
                """.formatted(bucket);
        return jdbcTemplate.query(sql, (rs, i) -> SignupPoint.builder()
                .bucketStart(toInstant(rs.getTimestamp("bucket_start")))
                .buyers(rs.getLong("buyers"))
                .suppliers(rs.getLong("suppliers"))
                .build(), period, period);
    }

    private List<CountPoint> loadRfqsOverTime(int period, String bucket) {
        String sql = seriesCte(bucket) + """
                SELECT b.s AS bucket_start, count(r.id) AS cnt
                FROM b
                LEFT JOIN rfqs r
                  ON date_trunc('%1$s', r.created_at) = b.s
                 AND r.created_at >= now() - make_interval(days => ?)
                GROUP BY b.s ORDER BY b.s
                """.formatted(bucket);
        return jdbcTemplate.query(sql, (rs, i) -> CountPoint.builder()
                .bucketStart(toInstant(rs.getTimestamp("bucket_start")))
                .count(rs.getLong("cnt"))
                .build(), period, period);
    }

    /** Every status in pipeline order, including zero counts, so the funnel shape is stable. */
    private List<LabelCount> loadRfqsByStatus() {
        Map<String, Long> counts = new HashMap<>();
        jdbcTemplate.query("SELECT status, count(*) AS cnt FROM rfqs GROUP BY status",
                rs -> { counts.put(rs.getString("status"), rs.getLong("cnt")); });

        List<LabelCount> result = new ArrayList<>();
        for (RfqStatus status : RfqStatus.values()) {
            result.add(new LabelCount(status.name(), counts.getOrDefault(status.name(), 0L)));
        }
        return result;
    }

    /** Top countries, with the long tail folded into "Other". */
    private List<LabelCount> loadUsersByCountry() {
        List<LabelCount> all = jdbcTemplate.query("""
                SELECT coalesce(nullif(trim(country), ''), 'Unknown') AS label, count(*) AS cnt
                FROM users GROUP BY 1 ORDER BY cnt DESC, label
                """, (rs, i) -> new LabelCount(rs.getString("label"), rs.getLong("cnt")));

        if (all.size() <= TOP_COUNTRIES + 1) {
            return all;
        }
        List<LabelCount> top = new ArrayList<>(all.subList(0, TOP_COUNTRIES));
        long other = all.subList(TOP_COUNTRIES, all.size()).stream().mapToLong(LabelCount::getCount).sum();
        top.add(new LabelCount("Other", other));
        return top;
    }

    private List<LabelCount> loadTopRfqCategories() {
        return jdbcTemplate.query("""
                SELECT coalesce(c.name, 'Uncategorised') AS label, count(*) AS cnt
                FROM rfqs r LEFT JOIN categories c ON c.id = r.category_id
                GROUP BY 1 ORDER BY cnt DESC, label LIMIT 6
                """, (rs, i) -> new LabelCount(rs.getString("label"), rs.getLong("cnt")));
    }

    /** Latest platform events across entities. No emails or contact details are exposed. */
    private List<Activity> loadRecentActivity() {
        return jdbcTemplate.query("""
                SELECT * FROM (
                  SELECT 'USER_REGISTERED' AS type, name AS title,
                         concat_ws(' · ', CASE role WHEN 'ROLE_BUYER' THEN 'Buyer'
                                                    WHEN 'ROLE_SUPPLIER' THEN 'Supplier'
                                                    ELSE 'Admin' END,
                                   nullif(company_name, ''), nullif(country, '')) AS subtitle,
                         created_at
                  FROM users
                  UNION ALL
                  SELECT 'MANUFACTURER_JOINED', name,
                         concat_ws(' · ', nullif(location, ''), nullif(country, ''),
                                   CASE WHEN verified THEN 'Verified' END),
                         created_at
                  FROM manufacturers
                  UNION ALL
                  SELECT 'RFQ_CREATED', product_name,
                         concat_ws(' · ', reference_number, nullif(company_name, ''), status),
                         created_at
                  FROM rfqs
                  UNION ALL
                  SELECT 'QUOTE_SUBMITTED', coalesce(r.product_name, 'RFQ quote'),
                         concat_ws(' · ', m.name, r.reference_number),
                         q.created_at
                  FROM rfq_quotes q
                  LEFT JOIN rfqs r ON r.id = q.rfq_id
                  LEFT JOIN manufacturers m ON m.id = q.manufacturer_id
                  UNION ALL
                  SELECT 'PRODUCT_ADDED', p.name, m.name, p.created_at
                  FROM products p LEFT JOIN manufacturers m ON m.id = p.manufacturer_id
                ) events
                WHERE created_at IS NOT NULL
                ORDER BY created_at DESC
                LIMIT 12
                """, (rs, i) -> Activity.builder()
                .type(rs.getString("type"))
                .title(rs.getString("title"))
                .subtitle(rs.getString("subtitle"))
                .occurredAt(toInstant(rs.getTimestamp("created_at")))
                .build());
    }

    /** One row per bucket so empty periods render as zero instead of disappearing. */
    private static String seriesCte(String bucket) {
        return """
                WITH b AS (
                  SELECT generate_series(
                           date_trunc('%1$s', now() - make_interval(days => ?)),
                           date_trunc('%1$s', now()),
                           interval '1 %1$s') AS s
                )
                """.formatted(bucket);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
