package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Request.manufacturer.ManufacturerUpdateRequest;
import seekfactory.axoraa.dto.Request.product.ProductCreateRequest;
import seekfactory.axoraa.dto.Request.reel.ReelCreateRequest;
import seekfactory.axoraa.dto.Request.rfq.RfqQuoteRequest;
import seekfactory.axoraa.dto.Response.factory.FactoryStatsResponse;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.Response.product.ProductResponse;
import seekfactory.axoraa.dto.Response.reel.ReelResponse;
import seekfactory.axoraa.dto.Response.rfq.RfqResponse;

import java.util.List;

public interface FactoryService {

    ManufacturerResponse getProfile(String userId);

    ManufacturerResponse updateProfile(String userId, ManufacturerUpdateRequest request);

    FactoryStatsResponse getStats(String userId);

    List<ProductResponse> getProducts(String userId);

    ProductResponse addProduct(String userId, ProductCreateRequest request);

    void deleteProduct(String userId, String productId);

    List<ReelResponse> getSeeks(String userId);

    ReelResponse addSeek(String userId, ReelCreateRequest request);

    void deleteSeek(String userId, String reelId);

    List<RfqResponse> getRfqs(String userId);

    void submitQuote(String userId, String rfqId, RfqQuoteRequest request);
}