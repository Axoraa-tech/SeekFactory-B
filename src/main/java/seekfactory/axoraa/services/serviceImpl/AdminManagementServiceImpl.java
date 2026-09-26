package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.ManufacturerDetail;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.ManufacturerRow;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.Page;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.PlanRequest;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.PlanRow;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.RfqRow;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.UserRow;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.SubscriptionPlan;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.entity.Rfqs.Rfq;
import seekfactory.axoraa.enums.RfqStatus;
import seekfactory.axoraa.enums.UserRole;
import seekfactory.axoraa.enums.VerificationStatus;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.Rfqs.RfqRepository;
import seekfactory.axoraa.repository.SubscriptionPlanRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.AdminManagementService;
import seekfactory.axoraa.utils.SecurityUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin list/search/moderation operations. Lists use SQL so that search, filters, counts and
 * paging run in the database. Every filter value is bound as a parameter; only fixed,
 * whitelisted SQL fragments are concatenated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminManagementServiceImpl implements AdminManagementService {

    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final RfqRepository rfqRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    /* ─────────────────────────── Users ─────────────────────────── */

    @Override
    @Transactional(readOnly = true)
    public Page<UserRow> listUsers(String q, String role, String status, int page, int size) {
        Where w = new Where();
        w.search(q, "u.name", "u.email", "u.company_name", "u.phone");
        if (role != null && !role.isBlank()) {
            UserRole parsed = parseEnum(UserRole.class, "ROLE_" + role.toUpperCase(Locale.ROOT).replace("ROLE_", ""), "role");
            w.and("u.role = ?", parsed.name());
        }
        if ("active".equalsIgnoreCase(status)) w.and("u.is_active");
        if ("inactive".equalsIgnoreCase(status)) w.and("NOT u.is_active");

        String from = " FROM users u LEFT JOIN manufacturers m ON m.user_id = u.id ";
        List<UserRow> items = jdbcTemplate.query("""
                SELECT u.id, u.name, u.email, u.phone, u.role, u.auth_provider, u.company_name, u.country,
                       u.is_active, coalesce(u.is_totp_enabled, false) AS totp, u.created_at,
                       m.id AS manufacturer_id, m.name AS manufacturer_name,
                       (SELECT count(*) FROM rfqs r WHERE r.user_id = u.id) AS rfq_count
                """ + from + w.sql() + " ORDER BY u.created_at DESC NULLS LAST LIMIT ? OFFSET ?",
                (rs, i) -> new UserRow(
                        rs.getString("id"), rs.getString("name"), rs.getString("email"), rs.getString("phone"),
                        rs.getString("role").replace("ROLE_", ""), rs.getString("auth_provider"),
                        rs.getString("company_name"), rs.getString("country"), rs.getBoolean("is_active"),
                        rs.getBoolean("totp"), rs.getString("manufacturer_id"), rs.getString("manufacturer_name"),
                        rs.getLong("rfq_count"), toInstant(rs.getTimestamp("created_at"))),
                w.params(pageSize(size), offset(page, size)));

        long total = count("SELECT count(*)" + from + w.sql(), w.params());
        Map<String, Long> counts = counts("""
                SELECT count(*) AS all_count,
                       count(*) FILTER (WHERE role = 'ROLE_BUYER')    AS buyer,
                       count(*) FILTER (WHERE role = 'ROLE_SUPPLIER') AS supplier,
                       count(*) FILTER (WHERE role = 'ROLE_ADMIN')    AS admin,
                       count(*) FILTER (WHERE is_active)              AS active,
                       count(*) FILTER (WHERE NOT is_active)          AS inactive
                FROM users
                """, "all_count", "buyer", "supplier", "admin", "active", "inactive");
        return new Page<>(items, total, Math.max(page, 0), pageSize(size), counts);
    }

    @Override
    public void setUserActive(String userId, boolean active, String currentAdminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (!active && userId.equals(currentAdminId)) {
            throw new BadRequestException("You cannot deactivate your own admin account.");
        }
        user.setIsActive(active);
        userRepository.save(user);
        log.info("Admin {} set user {} active={}", currentAdminId, userId, active);
    }

    /* ─────────────────────────── Manufacturers ─────────────────────────── */

    @Override
    @Transactional(readOnly = true)
    public Page<ManufacturerRow> listManufacturers(String q, String verified, int page, int size) {
        Where w = new Where();
        w.search(q, "m.name", "m.country", "m.location", "u.name", "u.email");
        if ("verified".equalsIgnoreCase(verified)) w.and("m.verification_status = 'APPROVED'");
        if ("unverified".equalsIgnoreCase(verified) || "pending".equalsIgnoreCase(verified))
            w.and("m.verification_status = 'PENDING'");
        if ("rejected".equalsIgnoreCase(verified)) w.and("m.verification_status = 'REJECTED'");
        if ("premium".equalsIgnoreCase(verified)) w.and("m.premium");

        String from = """
                 FROM manufacturers m
                 LEFT JOIN users u ON u.id = m.user_id
                 LEFT JOIN subscription_plans sp ON sp.id = m.subscription_id
                """;
        List<ManufacturerRow> items = jdbcTemplate.query("""
                SELECT m.id, m.slug, m.name, m.logo_url, m.country, m.location, m.verified, m.premium,
                       m.follower_count, m.created_at, m.verification_status, m.submitted_at,
                       sp.id AS plan_id, sp.name AS plan_name,
                       u.name AS owner_name, u.email AS owner_email,
                       (SELECT count(*) FROM products p WHERE p.manufacturer_id = m.id)   AS product_count,
                       (SELECT count(*) FROM reels r WHERE r.manufacturer_id = m.id)      AS reel_count,
                       (SELECT count(*) FROM rfq_quotes q WHERE q.manufacturer_id = m.id) AS quote_count
                """ + from + w.sql() + " ORDER BY (m.verification_status = 'PENDING') DESC, m.created_at DESC NULLS LAST LIMIT ? OFFSET ?",
                (rs, i) -> new ManufacturerRow(
                        rs.getString("id"), rs.getString("slug"), rs.getString("name"), rs.getString("logo_url"),
                        rs.getString("country"), rs.getString("location"), rs.getBoolean("verified"),
                        rs.getBoolean("premium"), rs.getString("plan_id"), rs.getString("plan_name"),
                        rs.getString("owner_name"), rs.getString("owner_email"), rs.getLong("product_count"),
                        rs.getLong("reel_count"), rs.getLong("quote_count"), rs.getInt("follower_count"),
                        toInstant(rs.getTimestamp("created_at")),
                        rs.getString("verification_status"), toInstant(rs.getTimestamp("submitted_at"))),
                w.params(pageSize(size), offset(page, size)));

        long total = count("SELECT count(*)" + from + w.sql(), w.params());
        Map<String, Long> counts = counts("""
                SELECT count(*) AS all_count,
                       count(*) FILTER (WHERE verification_status = 'APPROVED') AS verified,
                       count(*) FILTER (WHERE verification_status = 'PENDING')  AS unverified,
                       count(*) FILTER (WHERE verification_status = 'REJECTED') AS rejected,
                       count(*) FILTER (WHERE premium)                          AS premium
                FROM manufacturers
                """, "all_count", "verified", "unverified", "rejected", "premium");
        return new Page<>(items, total, Math.max(page, 0), pageSize(size), counts);
    }

    @Override
    @Transactional(readOnly = true)
    public ManufacturerDetail getManufacturer(String manufacturerId) {
        Manufacturer m = manufacturerRepository.findById(manufacturerId)
                .orElseThrow(() -> new ResourceNotFoundException("Manufacturer", "id", manufacturerId));
        User owner = m.getUser();

        List<String> categories = m.getCategories().stream().map(Category::getName).sorted().toList();
        List<String> exports = m.getExportCountries().stream().sorted().toList();
        List<String> certs = m.getCertifications().stream().sorted().toList();

        long products = count("SELECT count(*) FROM products WHERE manufacturer_id = ?", new Object[]{manufacturerId});
        long reels = count("SELECT count(*) FROM reels WHERE manufacturer_id = ?", new Object[]{manufacturerId});
        long quotes = count("SELECT count(*) FROM rfq_quotes WHERE manufacturer_id = ?", new Object[]{manufacturerId});

        return new ManufacturerDetail(
                m.getId(), m.getSlug(), m.getName(), m.getLogoUrl(), m.getCoverUrl(),
                m.getVerificationStatus() == null ? null : m.getVerificationStatus().name(),
                Boolean.TRUE.equals(m.getVerified()), Boolean.TRUE.equals(m.getPremium()),
                m.getReviewedBy(), m.getReviewedAt(), m.getRejectionReason(), m.getSubmittedAt(),
                m.getCompanyRegNumber(), m.getTaxId(), m.getRegistrationDate(), m.getFactoryAddress(), certs,
                m.getCountry(), m.getLocation(), m.getYearsEstablished(), m.getFactorySize(),
                m.getEmployees(), m.getDescription(), m.getChairmanName(), null, exports, categories,
                owner == null ? null : owner.getId(),
                owner == null ? null : owner.getName(),
                owner == null ? null : owner.getEmail(),
                owner == null ? null : owner.getPhone(),
                owner == null ? null : owner.getCountry(),
                owner == null ? null : owner.getCompanyName(),
                owner == null ? null : owner.getCreatedAt(),
                products, reels, quotes, m.getFollowerCount(),
                m.getSubscriptionPlan() == null ? null : m.getSubscriptionPlan().getId(),
                m.getSubscriptionPlan() == null ? null : m.getSubscriptionPlan().getName(),
                m.getCreatedAt());
    }

    /**
     * Records an admin decision. `verified` stays in step with the status because the
     * rest of the app (feed, listings, badge) reads that flag.
     */
    @Override
    public void reviewManufacturer(String manufacturerId, boolean approve, String reason, String currentAdminId) {
        Manufacturer m = manufacturerRepository.findById(manufacturerId)
                .orElseThrow(() -> new ResourceNotFoundException("Manufacturer", "id", manufacturerId));

        if (!approve && (reason == null || reason.isBlank())) {
            throw new BadRequestException("A reason is required when rejecting a manufacturer.");
        }

        m.setVerificationStatus(approve ? VerificationStatus.APPROVED : VerificationStatus.REJECTED);
        m.setVerified(approve);
        m.setRejectionReason(approve ? null : reason.trim());
        m.setReviewedBy(currentAdminId);
        m.setReviewedAt(Instant.now());
        manufacturerRepository.save(m);

        log.info("Admin {} {} manufacturer {}", currentAdminId, approve ? "approved" : "rejected", manufacturerId);
    }

    @Override
    public void setManufacturerVerified(String manufacturerId, boolean verified) {
        reviewManufacturer(manufacturerId, verified, verified ? null : "Verification withdrawn by an administrator.",
                SecurityUtils.getCurrentUserId());
    }

    @Override
    public void setManufacturerPlan(String manufacturerId, String planId) {
        Manufacturer m = manufacturerRepository.findById(manufacturerId)
                .orElseThrow(() -> new ResourceNotFoundException("Manufacturer", "id", manufacturerId));
        if (planId == null || planId.isBlank()) {
            m.setSubscriptionPlan(null);
            m.setPremium(false);
        } else {
            SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                    .orElseThrow(() -> new ResourceNotFoundException("SubscriptionPlan", "id", planId));
            m.setSubscriptionPlan(plan);
            // Premium follows the plan price both ways, so a downgrade to a free plan clears it
            m.setPremium(plan.getPriceUsd().signum() > 0);
        }
        manufacturerRepository.save(m);
    }

    /* ─────────────────────────── RFQs ─────────────────────────── */

    @Override
    @Transactional(readOnly = true)
    public Page<RfqRow> listRfqs(String q, String status, int page, int size) {
        Where w = new Where();
        w.search(q, "r.product_name", "r.reference_number", "r.company_name", "u.name", "u.email");
        if (status != null && !status.isBlank()) {
            w.and("r.status = ?", parseEnum(RfqStatus.class, status.toUpperCase(Locale.ROOT), "status").name());
        }

        String from = """
                 FROM rfqs r
                 LEFT JOIN users u ON u.id = r.user_id
                 LEFT JOIN categories c ON c.id = r.category_id
                """;
        List<RfqRow> items = jdbcTemplate.query("""
                SELECT r.id, r.reference_number, r.product_name, r.quantity, r.unit, r.target_price,
                       r.currency, r.incoterm, r.company_name, r.details, r.status, r.created_at,
                       c.name AS category_name, u.name AS buyer_name, u.email AS buyer_email,
                       (SELECT count(*) FROM rfq_quotes q WHERE q.rfq_id = r.id) AS quote_count
                """ + from + w.sql() + " ORDER BY r.created_at DESC NULLS LAST LIMIT ? OFFSET ?",
                (rs, i) -> new RfqRow(
                        rs.getString("id"), rs.getString("reference_number"), rs.getString("product_name"),
                        rs.getString("quantity"), rs.getString("unit"), rs.getString("target_price"),
                        rs.getString("currency"), rs.getString("incoterm"), rs.getString("company_name"),
                        rs.getString("details"), rs.getString("category_name"), rs.getString("buyer_name"),
                        rs.getString("buyer_email"), rs.getString("status"), rs.getLong("quote_count"),
                        toInstant(rs.getTimestamp("created_at"))),
                w.params(pageSize(size), offset(page, size)));

        long total = count("SELECT count(*)" + from + w.sql(), w.params());
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all_count", 0L);
        for (RfqStatus s : RfqStatus.values()) counts.put(s.name(), 0L);
        jdbcTemplate.query("SELECT status, count(*) AS cnt FROM rfqs GROUP BY status", rs -> {
            counts.merge(rs.getString("status"), rs.getLong("cnt"), Long::sum);
            counts.merge("all_count", rs.getLong("cnt"), Long::sum);
        });
        return new Page<>(items, total, Math.max(page, 0), pageSize(size), counts);
    }

    @Override
    public void setRfqStatus(String rfqId, String status) {
        Rfq rfq = rfqRepository.findById(rfqId)
                .orElseThrow(() -> new ResourceNotFoundException("RFQ", "id", rfqId));
        rfq.setStatus(parseEnum(RfqStatus.class, status.toUpperCase(Locale.ROOT), "status"));
        rfqRepository.save(rfq);
    }

    /* ─────────────────────────── Pricing plans ─────────────────────────── */

    @Override
    @Transactional(readOnly = true)
    public List<PlanRow> listPlans() {
        return jdbcTemplate.query("""
                SELECT sp.id, sp.name, sp.price_usd, sp.features_json::text AS features, sp.created_at,
                       (SELECT count(*) FROM manufacturers m WHERE m.subscription_id = sp.id) AS mcount
                FROM subscription_plans sp ORDER BY sp.price_usd, sp.name
                """, (rs, i) -> new PlanRow(
                rs.getString("id"), rs.getString("name"), rs.getBigDecimal("price_usd"),
                rs.getString("features"), rs.getLong("mcount"), toInstant(rs.getTimestamp("created_at"))));
    }

    @Override
    public void createPlan(PlanRequest request) {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name(request.name().trim())
                .priceUsd(request.priceUsd())
                .featuresJson(featuresJson(request.features()))
                .build();
        subscriptionPlanRepository.save(plan);
    }

    @Override
    public void updatePlan(String planId, PlanRequest request) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionPlan", "id", planId));
        plan.setName(request.name().trim());
        plan.setPriceUsd(request.priceUsd());
        plan.setFeaturesJson(featuresJson(request.features()));
        subscriptionPlanRepository.save(plan);
        // Keep the premium flag consistent for everyone already on this plan
        jdbcTemplate.update("UPDATE manufacturers SET premium = ? WHERE subscription_id = ?",
                request.priceUsd().signum() > 0, planId);
    }

    @Override
    public void deletePlan(String planId) {
        if (!subscriptionPlanRepository.existsById(planId)) {
            throw new ResourceNotFoundException("SubscriptionPlan", "id", planId);
        }
        Long inUse = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM manufacturers WHERE subscription_id = ?", Long.class, planId);
        if (inUse != null && inUse > 0) {
            throw new BadRequestException("This plan is assigned to " + inUse
                    + " manufacturer(s). Move them to another plan before deleting it.");
        }
        subscriptionPlanRepository.deleteById(planId);
    }

    /* ─────────────────────────── helpers ─────────────────────────── */

    private static String featuresJson(List<String> features) {
        StringBuilder sb = new StringBuilder("{\"features\":[");
        List<String> list = features == null ? List.of() : features;
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(jsonEscape(list.get(i).trim())).append('"');
        }
        return sb.append("]}").toString();
    }

    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid " + field + ": " + value);
        }
    }

    private long count(String sql, Object[] params) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class, params);
        return n == null ? 0 : n;
    }

    private Map<String, Long> counts(String sql, String... columns) {
        return jdbcTemplate.queryForObject(sql, (rs, i) -> {
            Map<String, Long> m = new LinkedHashMap<>();
            for (String c : columns) m.put(c, rs.getLong(c));
            return m;
        });
    }

    private static int pageSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private static int offset(int page, int size) {
        return Math.max(page, 0) * pageSize(size);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    /** Collects AND-ed conditions with bound parameters. */
    private static final class Where {
        private final List<String> clauses = new ArrayList<>();
        private final List<Object> params = new ArrayList<>();

        void and(String clause, Object... values) {
            clauses.add(clause);
            params.addAll(List.of(values));
        }

        void search(String q, String... columns) {
            if (q == null || q.isBlank()) return;
            String like = "%" + q.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            List<String> ors = new ArrayList<>();
            for (String col : columns) {
                ors.add("lower(coalesce(" + col + ", '')) LIKE ?");
                params.add(like);
            }
            clauses.add("(" + String.join(" OR ", ors) + ")");
        }

        String sql() {
            return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        }

        Object[] params(Object... extra) {
            List<Object> all = new ArrayList<>(params);
            all.addAll(List.of(extra));
            return all.toArray();
        }
    }
}
