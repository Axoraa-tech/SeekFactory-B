package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.Page;
import seekfactory.axoraa.dto.Response.payment.PaymentDtos.MyPayment;
import seekfactory.axoraa.dto.Response.payment.PaymentDtos.PaymentDetail;
import seekfactory.axoraa.dto.Response.payment.PaymentDtos.PaymentRow;
import seekfactory.axoraa.dto.Response.payment.PaymentDtos.Proof;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.BuyerPlan;
import seekfactory.axoraa.enums.NotificationType;
import seekfactory.axoraa.enums.UserRole;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.NotificationService;
import seekfactory.axoraa.services.services.PlanPaymentService;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PlanPaymentServiceImpl implements PlanPaymentService {

    private static final long MAX_PROOF_BYTES = 5L * 1024 * 1024;
    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /* ─────────────────────────── Payer side ─────────────────────────── */

    @Override
    public MyPayment submit(String userId, String plan, String region, String reference, MultipartFile proof) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (user.getRole() != UserRole.ROLE_BUYER) {
            throw new BadRequestException("Plan payments are available to buyer accounts only for now");
        }

        BuyerPlan buyerPlan = parsePaidPlan(plan);
        if (user.getBuyerPlan() == buyerPlan) {
            throw new BadRequestException("You are already on the " + buyerPlan.name().toLowerCase(Locale.ROOT) + " plan");
        }
        boolean india = parseRegion(region);

        // Price comes from the database at submission time; the client never sends an amount
        Map<String, Object> priced = jdbcTemplate.queryForList(
                "SELECT name, price_inr, price_cny FROM buyer_plans WHERE code = ?", buyerPlan.name())
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("BuyerPlan", "code", buyerPlan.name()));
        BigDecimal amount = (BigDecimal) priced.get(india ? "price_inr" : "price_cny");
        String currency = india ? "INR" : "CNY";

        byte[] bytes = readProof(proof);
        String contentType = sniffContentType(bytes);

        String id = UUID.randomUUID().toString();
        try {
            jdbcTemplate.update("""
                    INSERT INTO plan_payments (id, user_id, account_type, buyer_plan, plan_name, region, currency, amount,
                        payer_reference, payer_name, payer_email, payer_phone, payer_company, payer_country, payer_address,
                        proof_data, proof_content_type, proof_filename, proof_size)
                    VALUES (?, ?, 'BUYER', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    id, userId, buyerPlan.name(), (String) priced.get("name"), india ? "india" : "china", currency, amount,
                    blankToNull(reference, 120), user.getName(), user.getEmail(), user.getPhone(), user.getCompanyName(),
                    user.getCountry(), user.getAddress(),
                    bytes, contentType, safeFilename(proof.getOriginalFilename()), bytes.length);
        } catch (DuplicateKeyException e) {
            throw new BadRequestException("You already have a payment waiting for review. Please wait for the decision.");
        }
        log.info("Payment {} submitted by user {} for plan {}", id, userId, buyerPlan);
        return jdbcTemplate.queryForObject(MY_SELECT + " WHERE id = ?", MY_MAPPER, id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MyPayment> listMine(String userId) {
        return jdbcTemplate.query(MY_SELECT + " WHERE user_id = ? ORDER BY created_at DESC LIMIT 20", MY_MAPPER, userId);
    }

    /* ─────────────────────────── Admin side ─────────────────────────── */

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentRow> list(String status, String q, int page, int size) {
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            clauses.add("status = ?");
            params.add(parseStatus(status));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            clauses.add("(lower(payer_name) LIKE ? OR lower(payer_email) LIKE ? OR lower(payer_company) LIKE ?)");
            params.add(like);
            params.add(like);
            params.add(like);
        }
        String where = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int pageNo = Math.max(page, 0);

        // proof_data is deliberately not selected: lists must stay small
        List<Object> listParams = new ArrayList<>(params);
        listParams.add(pageSize);
        listParams.add(pageNo * pageSize);
        List<PaymentRow> items = jdbcTemplate.query("""
                SELECT id, account_type, plan_name, currency, amount, status, payer_name, payer_email,
                       payer_company, payer_country, created_at
                FROM plan_payments""" + where + " ORDER BY (status = 'PENDING') DESC, created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> new PaymentRow(
                        rs.getString("id"), rs.getString("account_type"), rs.getString("plan_name"),
                        rs.getString("currency"), rs.getBigDecimal("amount"), rs.getString("status"),
                        rs.getString("payer_name"), rs.getString("payer_email"), rs.getString("payer_company"),
                        rs.getString("payer_country"), toInstant(rs.getTimestamp("created_at"))),
                listParams.toArray());

        Long total = jdbcTemplate.queryForObject("SELECT count(*) FROM plan_payments" + where, Long.class, params.toArray());
        Map<String, Long> counts = jdbcTemplate.queryForObject("""
                SELECT count(*) AS all_count,
                       count(*) FILTER (WHERE status = 'PENDING')  AS pending,
                       count(*) FILTER (WHERE status = 'APPROVED') AS approved,
                       count(*) FILTER (WHERE status = 'REJECTED') AS rejected
                FROM plan_payments
                """, (rs, i) -> {
            Map<String, Long> m = new LinkedHashMap<>();
            for (String c : List.of("all_count", "pending", "approved", "rejected")) m.put(c, rs.getLong(c));
            return m;
        });
        return new Page<>(items, total == null ? 0 : total, pageNo, pageSize, counts);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentDetail get(String paymentId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT p.id, p.account_type, p.plan_name, p.buyer_plan, p.region, p.currency, p.amount,
                           p.payer_reference, p.status, p.user_id, p.payer_name, p.payer_email, p.payer_phone,
                           p.payer_company, p.payer_country, p.payer_address, p.manufacturer_id, m.name AS manufacturer_name,
                           p.proof_content_type, p.proof_filename, p.proof_size,
                           p.reviewed_by, p.reviewed_at, p.rejection_reason, p.created_at
                    FROM plan_payments p LEFT JOIN manufacturers m ON m.id = p.manufacturer_id
                    WHERE p.id = ?
                    """, (rs, i) -> new PaymentDetail(
                    rs.getString("id"), rs.getString("account_type"), rs.getString("plan_name"),
                    lower(rs.getString("buyer_plan")), rs.getString("region"), rs.getString("currency"),
                    rs.getBigDecimal("amount"), rs.getString("payer_reference"), rs.getString("status"),
                    rs.getString("user_id"), rs.getString("payer_name"), rs.getString("payer_email"),
                    rs.getString("payer_phone"), rs.getString("payer_company"), rs.getString("payer_country"),
                    rs.getString("payer_address"), rs.getString("manufacturer_id"), rs.getString("manufacturer_name"),
                    rs.getString("proof_content_type"), rs.getString("proof_filename"), rs.getInt("proof_size"),
                    rs.getString("reviewed_by"), toInstant(rs.getTimestamp("reviewed_at")),
                    rs.getString("rejection_reason"), toInstant(rs.getTimestamp("created_at"))), paymentId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResourceNotFoundException("Payment", "id", paymentId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Proof proof(String paymentId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT proof_data, proof_content_type, proof_filename FROM plan_payments WHERE id = ?",
                    (rs, i) -> new Proof(rs.getBytes("proof_data"), rs.getString("proof_content_type"),
                            rs.getString("proof_filename")), paymentId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResourceNotFoundException("Payment", "id", paymentId);
        }
    }

    @Override
    public void approve(String paymentId, String adminId) {
        Map<String, Object> row = lockPending(paymentId);
        String userId = (String) row.get("user_id");

        if ("BUYER".equals(row.get("account_type"))) {
            BuyerPlan plan = BuyerPlan.valueOf((String) row.get("buyer_plan"));
            jdbcTemplate.update("UPDATE users SET buyer_plan = ? WHERE id = ?", plan.name(), userId);
        } else {
            throw new BadRequestException("Manufacturer plan payments are not supported yet");
        }

        jdbcTemplate.update(
                "UPDATE plan_payments SET status = 'APPROVED', reviewed_by = ?, reviewed_at = now(), rejection_reason = NULL WHERE id = ?",
                adminId, paymentId);
        notifyPayer(userId, "Payment approved",
                "Your " + row.get("plan_name") + " plan is now active. Thank you!", paymentId);
        log.info("Admin {} approved payment {}", adminId, paymentId);
    }

    @Override
    public void reject(String paymentId, String adminId, String reason) {
        Map<String, Object> row = lockPending(paymentId);
        jdbcTemplate.update(
                "UPDATE plan_payments SET status = 'REJECTED', reviewed_by = ?, reviewed_at = now(), rejection_reason = ? WHERE id = ?",
                adminId, reason.trim(), paymentId);
        notifyPayer((String) row.get("user_id"), "Payment not approved",
                "We could not confirm your payment: " + reason.trim() + " You can submit a new proof.", paymentId);
        log.info("Admin {} rejected payment {}", adminId, paymentId);
    }

    /* ─────────────────────────── helpers ─────────────────────────── */

    private static final String MY_SELECT = """
            SELECT id, plan_name, buyer_plan, currency, amount, status, rejection_reason, created_at, reviewed_at
            FROM plan_payments""";

    private static final RowMapper<MyPayment> MY_MAPPER = (rs, i) -> new MyPayment(
            rs.getString("id"), rs.getString("plan_name"), lower(rs.getString("buyer_plan")), rs.getString("currency"),
            rs.getBigDecimal("amount"), rs.getString("status"), rs.getString("rejection_reason"),
            toInstant(rs.getTimestamp("created_at")), toInstant(rs.getTimestamp("reviewed_at")));

    /** Locks the row so two admins cannot decide the same payment, and requires it to still be pending. */
    private Map<String, Object> lockPending(String paymentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT user_id, account_type, buyer_plan, plan_name, status FROM plan_payments WHERE id = ? FOR UPDATE",
                paymentId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Payment", "id", paymentId);
        }
        Map<String, Object> row = rows.get(0);
        if (!"PENDING".equals(row.get("status"))) {
            throw new BadRequestException("This payment has already been " + ((String) row.get("status")).toLowerCase(Locale.ROOT));
        }
        return row;
    }

    private void notifyPayer(String userId, String title, String body, String paymentId) {
        userRepository.findById(userId)
                .ifPresent(u -> notificationService.notify(u, NotificationType.SYSTEM, title, body, paymentId));
    }

    private static BuyerPlan parsePaidPlan(String plan) {
        try {
            BuyerPlan parsed = BuyerPlan.valueOf(plan.trim().toUpperCase(Locale.ROOT));
            if (parsed == BuyerPlan.FREE) {
                throw new BadRequestException("The free plan does not need a payment");
            }
            return parsed;
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown plan: " + plan);
        }
    }

    private static boolean parseRegion(String region) {
        if ("india".equalsIgnoreCase(region)) return true;
        if ("china".equalsIgnoreCase(region)) return false;
        throw new BadRequestException("region must be 'india' or 'china'");
    }

    private static String parseStatus(String status) {
        String s = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("PENDING", "APPROVED", "REJECTED").contains(s)) {
            throw new BadRequestException("Unknown status: " + status);
        }
        return s;
    }

    private static byte[] readProof(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please attach your payment screenshot or invoice");
        }
        if (file.getSize() > MAX_PROOF_BYTES) {
            throw new BadRequestException("The file is too large. The limit is 5 MB.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file");
        }
    }

    /** Decides the type from the file's own bytes, never from the client-supplied name or header. */
    static String sniffContentType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') return "application/pdf";
        throw new BadRequestException("Only JPG, PNG, WebP or PDF files are accepted");
    }

    private static String safeFilename(String name) {
        if (name == null || name.isBlank()) return null;
        String clean = name.replaceAll("[^A-Za-z0-9._ -]", "_");
        return clean.length() > 200 ? clean.substring(clean.length() - 200) : clean;
    }

    private static String blankToNull(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
