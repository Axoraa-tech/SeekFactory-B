package seekfactory.axoraa.dto.Response.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Request/response shapes for the admin management screens (users, manufacturers, RFQs, pricing).
 */
public final class AdminManagementDtos {

    private AdminManagementDtos() {}

    /** A page of rows plus per-filter counts so the UI can label its filter chips. */
    public record Page<T>(List<T> items, long total, int page, int size, Map<String, Long> counts) {}

    public record UserRow(
            String id, String name, String email, String phone, String role, String authProvider,
            String companyName, String country, boolean active, boolean totpEnabled,
            String manufacturerId, String manufacturerName, long rfqCount, Instant createdAt) {}

    public record ManufacturerRow(
            String id, String slug, String name, String logoUrl, String country, String location,
            boolean verified, boolean premium, String planId, String planName,
            String ownerName, String ownerEmail, long productCount, long reelCount, long quoteCount,
            int followerCount, Instant createdAt,
            String verificationStatus, Instant submittedAt) {}

    /**
     * Everything an admin needs to decide on one factory, in a single call.
     * Deliberately fuller than {@link ManufacturerRow}: the review screen shows the
     * whole application rather than the columns that fit in a table.
     */
    public record ManufacturerDetail(
            // identity
            String id, String slug, String name, String logoUrl, String coverUrl,
            // review state
            String verificationStatus, boolean verified, boolean premium,
            String reviewedBy, Instant reviewedAt, String rejectionReason, Instant submittedAt,
            // submitted business details
            String companyRegNumber, String taxId, String registrationDate, String factoryAddress,
            List<String> certifications,
            // profile
            String country, String location, Integer yearsEstablished, String factorySize,
            String employees, String description, String chairmanName, String websiteUrl,
            List<String> exportCountries, List<String> categories,
            // owner account
            String ownerId, String ownerName, String ownerEmail, String ownerPhone,
            String ownerCountry, String ownerCompanyName, Instant ownerJoinedAt,
            // activity
            long productCount, long reelCount, long quoteCount, int followerCount,
            String planId, String planName, Instant createdAt) {}

    public record RfqRow(
            String id, String referenceNumber, String productName, String quantity, String unit,
            String targetPrice, String currency, String incoterm, String companyName, String details,
            String categoryName, String buyerName, String buyerEmail, String status,
            long quoteCount, Instant createdAt) {}

    public record PlanRow(
            String id, String name, BigDecimal priceUsd, String featuresJson,
            long manufacturerCount, Instant createdAt) {}

    public record RfqStatusRequest(@NotBlank String status) {}

    public record PlanRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @DecimalMin("0.00") BigDecimal priceUsd,
            List<@NotBlank @Size(max = 200) String> features) {}
}
