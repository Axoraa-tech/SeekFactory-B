package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final Set<Integer> ALLOWED_PERIODS = Set.of(7, 30, 90, 365);
    private static final int TOP_COUNTRIES = 7;

    /**
     * Ids written by the V5 demo seed ('usr-admin', 'mfg-01', 'reel-01', ...). Every real row gets a UUID
     * from BaseEntity, which can never start with one of these prefixes, so the dashboard can leave the
     * demo rows out of its figures without deleting them. A test keeps this in step with the seed file.
     */
    static final String DEMO_ID_REGEX = "^(usr|mfg|prod|reel|com|rfq|quote|conv|msg|notif)-";

    /** SQL predicate that is true for real (non-demo) rows; pass the qualified id column. */
    private static String real(String idColumn) {
        return idColumn + " !~ '" + DEMO_ID_REGEX + "'";
    }

    private final UserRepository userRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final RfqRepository rfqRepository;
    private final JdbcTemplate jdbcTemplate;

    /** Cheap virtual threads; concurrency is bounded by the Hikari pool, not by this executor. */
    private final ExecutorService queryExecutor = Executors.newVirtualThreadPerTaskExecutor();

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

    /**
     * Runs the independent read queries concurrently. They are separate round trips to a remote
     * database, so running them one after another made the dashboard take several seconds. No class-level
     * transaction here: each query borrows its own pooled connection on its worker thread.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AdminAnalyticsResponse getAnalytics(int days) {
        int period = ALLOWED_PERIODS.contains(days) ? days : 30;
        // Whitelisted, never user-supplied text, so it is safe to inline into date_trunc / interval
        String bucket = period <= 31 ? "day" : period <= 90 ? "week" : "month";

        var totals = async(this::loadTotals);
        var kpis = async(() -> loadKpis(period));
        var signups = async(() -> loadSignups(period, bucket));
        var rfqsOverTime = async(() -> loadRfqsOverTime(period, bucket));
        var rfqsByStatus = async(this::loadRfqsByStatus);
        var usersByCountry = async(this::loadUsersByCountry);
        var topRfqCategories = async(this::loadTopRfqCategories);
        var recentActivity = async(this::loadRecentActivity);

        return AdminAnalyticsResponse.builder()
                .periodDays(period)
                .bucket(bucket)
                .generatedAt(Instant.now())
                .totals(totals.join())
                .kpis(kpis.join())
                .signups(signups.join())
                .rfqsOverTime(rfqsOverTime.join())
                .rfqsByStatus(rfqsByStatus.join())
                .usersByCountry(usersByCountry.join())
                .topRfqCategories(topRfqCategories.join())
                .recentActivity(recentActivity.join())
                .build();
    }

    private <T> CompletableFuture<T> async(Supplier<T> query) {
        return CompletableFuture.supplyAsync(query, queryExecutor);
    }

    private Totals loadTotals() {
        String sql = """
                SELECT
                  (SELECT count(*) FROM users WHERE %1$s)                                    AS users,
                  (SELECT count(*) FROM users WHERE role = 'ROLE_BUYER' AND %1$s)            AS buyers,
                  (SELECT count(*) FROM users WHERE role = 'ROLE_SUPPLIER' AND %1$s)         AS suppliers,
                  (SELECT count(*) FROM users WHERE role = 'ROLE_ADMIN' AND %1$s)            AS admins,
                  (SELECT count(*) FROM users WHERE is_active AND %1$s)                      AS active_users,
                  (SELECT count(*) FROM manufacturers WHERE %1$s)                            AS manufacturers,
                  (SELECT count(*) FROM manufacturers WHERE verified AND %1$s)               AS verified_manufacturers,
                  (SELECT count(*) FROM manufacturers WHERE premium AND %1$s)                AS premium_manufacturers,
                  (SELECT count(*) FROM products WHERE %1$s)                                 AS products,
                  (SELECT count(*) FROM products WHERE is_active AND %1$s)                   AS active_products,
                  (SELECT count(*) FROM reels WHERE %1$s)                                    AS reels,
                  (SELECT coalesce(sum(views_count), 0) FROM reels WHERE %1$s)               AS reel_views,
                  (SELECT count(*) FROM rfqs WHERE %1$s)                                     AS rfqs,
                  (SELECT count(*) FROM rfqs WHERE status = 'SUBMITTED' AND %1$s)            AS pending_rfqs,
                  (SELECT count(*) FROM rfq_quotes WHERE %1$s)                               AS quotes,
                  (SELECT count(*) FROM conversations WHERE %1$s)                            AS conversations,
                  (SELECT count(*) FROM messages WHERE %1$s)                                 AS messages
                """.formatted(real("id"));
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
        String real = real("id") + " AND";
        sources.put("buyers", "users WHERE role = 'ROLE_BUYER' AND " + real);
        sources.put("suppliers", "users WHERE role = 'ROLE_SUPPLIER' AND " + real);
        sources.put("manufacturers", "manufacturers WHERE " + real);
        sources.put("products", "products WHERE " + real);
        sources.put("rfqs", "rfqs WHERE " + real);
        sources.put("quotes", "rfq_quotes WHERE " + real);
        sources.put("messages", "messages WHERE " + real);

        // One UNION ALL statement instead of one query per table: the database is remote, so each
        // separate statement costs a full network round trip.
        List<String> branches = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        sources.forEach((key, from) -> {
            branches.add("SELECT '" + key + "' AS key,"
                    + " count(*) FILTER (WHERE created_at >= now() - make_interval(days => ?)) AS cur,"
                    + " count(*) FILTER (WHERE created_at >= now() - make_interval(days => ?)"
                    + "                    AND created_at <  now() - make_interval(days => ?)) AS prev"
                    + " FROM " + from + " created_at >= now() - make_interval(days => ?)");
            params.addAll(List.of(period, period * 2, period, period * 2));
        });
        return jdbcTemplate.query(String.join(" UNION ALL ", branches), (rs, i) -> Kpi.builder()
                .key(rs.getString("key"))
                .current(rs.getLong("cur"))
                .previous(rs.getLong("prev"))
                .build(), params.toArray());
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
                 AND %2$s
                GROUP BY b.s ORDER BY b.s
                """.formatted(bucket, real("u.id"));
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
                 AND %2$s
                GROUP BY b.s ORDER BY b.s
                """.formatted(bucket, real("r.id"));
        return jdbcTemplate.query(sql, (rs, i) -> CountPoint.builder()
                .bucketStart(toInstant(rs.getTimestamp("bucket_start")))
                .count(rs.getLong("cnt"))
                .build(), period, period);
    }

    /** Every status in pipeline order, including zero counts, so the funnel shape is stable. */
    private List<LabelCount> loadRfqsByStatus() {
        Map<String, Long> counts = new HashMap<>();
        jdbcTemplate.query("SELECT status, count(*) AS cnt FROM rfqs WHERE " + real("id") + " GROUP BY status",
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
                FROM users WHERE %s GROUP BY 1 ORDER BY cnt DESC, label
                """.formatted(real("id")), (rs, i) -> new LabelCount(rs.getString("label"), rs.getLong("cnt")));

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
                WHERE %s
                GROUP BY 1 ORDER BY cnt DESC, label LIMIT 6
                """.formatted(real("r.id")), (rs, i) -> new LabelCount(rs.getString("label"), rs.getLong("cnt")));
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
                  FROM users WHERE %1$s
                  UNION ALL
                  SELECT 'MANUFACTURER_JOINED', name,
                         concat_ws(' · ', nullif(location, ''), nullif(country, ''),
                                   CASE WHEN verified THEN 'Verified' END),
                         created_at
                  FROM manufacturers WHERE %2$s
                  UNION ALL
                  SELECT 'RFQ_CREATED', product_name,
                         concat_ws(' · ', reference_number, nullif(company_name, ''), status),
                         created_at
                  FROM rfqs WHERE %3$s
                  UNION ALL
                  SELECT 'QUOTE_SUBMITTED', coalesce(r.product_name, 'RFQ quote'),
                         concat_ws(' · ', m.name, r.reference_number),
                         q.created_at
                  FROM rfq_quotes q
                  LEFT JOIN rfqs r ON r.id = q.rfq_id
                  LEFT JOIN manufacturers m ON m.id = q.manufacturer_id
                  WHERE %4$s
                  UNION ALL
                  SELECT 'PRODUCT_ADDED', p.name, m.name, p.created_at
                  FROM products p LEFT JOIN manufacturers m ON m.id = p.manufacturer_id
                  WHERE %5$s
                ) events
                WHERE created_at IS NOT NULL
                ORDER BY created_at DESC
                LIMIT 12
                """.formatted(real("id"), real("id"), real("id"), real("q.id"), real("p.id")),
                (rs, i) -> Activity.builder()
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
