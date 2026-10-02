package seekfactory.axoraa.services.serviceImpl;

import seekfactory.axoraa.dto.Response.rfq.RfqResponse;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.entity.Rfqs.Rfq;

/** Copies an RFQ's target (factory, product, source seek) onto its response, for buyer and seller views. */
final class RfqTargets {

    private RfqTargets() {
    }

    static void apply(RfqResponse response, Rfq rfq) {
        Manufacturer manufacturer = rfq.getManufacturer();
        if (manufacturer != null) {
            response.setManufacturerId(manufacturer.getId());
            response.setManufacturerName(manufacturer.getName());
            response.setManufacturerSlug(manufacturer.getSlug());
        }
        Product product = rfq.getProduct();
        if (product != null) {
            response.setProductId(product.getId());
            response.setProductSlug(product.getSlug());
            response.setProductImageUrl(product.getImageUrl());
        }
        if (rfq.getSourceReel() != null) {
            response.setSourceReelId(rfq.getSourceReel().getId());
        }
    }
}
