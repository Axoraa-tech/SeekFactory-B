package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Request.manufacturer.ManufacturerUpdateRequest;
import seekfactory.axoraa.dto.Request.manufacturer.VerificationSubmitRequest;
import seekfactory.axoraa.dto.Request.product.ProductUpdateRequest;
import seekfactory.axoraa.dto.Request.reel.ReelUpdateRequest;
import seekfactory.axoraa.dto.Response.manufacturer.VerificationResponse;
import seekfactory.axoraa.dto.Request.product.ProductCreateRequest;
import seekfactory.axoraa.dto.Request.reel.ReelCreateRequest;
import seekfactory.axoraa.dto.Request.rfq.RfqQuoteRequest;
import seekfactory.axoraa.dto.Response.factory.FactoryStatsResponse;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.Response.product.ProductResponse;
import seekfactory.axoraa.dto.Response.reel.ReelResponse;
import seekfactory.axoraa.dto.Response.rfq.RfqResponse;
import seekfactory.axoraa.entity.Manufacturer;

import java.util.List;

public interface FactoryService {

    ManufacturerResponse getProfile(String userId);

    ManufacturerResponse updateProfile(String userId, ManufacturerUpdateRequest request);

    FactoryStatsResponse getStats(String userId);

    List<ProductResponse> getProducts(String userId);

    ProductResponse addProduct(String userId, ProductCreateRequest request);

    void deleteProduct(String userId, String productId);

    ProductResponse updateProduct(String userId, String productId, ProductUpdateRequest request);

    ProductResponse setProductListed(String userId, String productId, boolean listed);

    List<ReelResponse> getSeeks(String userId);

    ReelResponse addSeek(String userId, ReelCreateRequest request);

    void deleteSeek(String userId, String reelId);

    ReelResponse updateSeek(String userId, String reelId, ReelUpdateRequest request);

    ReelResponse setSeekListed(String userId, String reelId, boolean listed);

    VerificationResponse getVerification(String userId);

    VerificationResponse submitVerification(String userId, VerificationSubmitRequest request);

    /** Throws unless the RFQ is routed to this supplier's factory (same rule as the RFQ list). */
    void assertRfqRouted(String userId, String rfqId);

    List<RfqResponse> getRfqs(String userId);

    void submitQuote(String userId, String rfqId, RfqQuoteRequest request);

    /** Public responsiveness figures shown on the factory's buyer-facing profile. */
    ResponseMetrics getResponseMetrics(Manufacturer manufacturer);
}