package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.Test;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.Response.product.ProductResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogListTrimTest {

    private static final String LONG = ("Heavy duty hydraulic press brake with CNC back gauge, servo pump, "
            + "European tooling and a two year warranty. ").repeat(12);

    @Test
    void summaryCutsAtAWordAndMarksIt() {
        String s = ProductServiceImpl.summary(LONG, 240);
        assertThat(s.length()).isLessThanOrEqualTo(241);
        assertThat(s).endsWith("…").doesNotContain("  ");
        assertThat(s.substring(0, s.length() - 1)).doesNotEndWith(" ");
        assertThat(LONG).startsWith(s.substring(0, s.length() - 1));
        assertThat(ProductServiceImpl.summary("Short\n text ", 240)).isEqualTo("Short text");
        assertThat(ProductServiceImpl.summary(null, 240)).isNull();
    }

    @Test
    void catalogueProductsKeepAPreviewOnly() {
        ProductResponse p = new ProductResponse();
        p.setName("Press brake");
        p.setDescription(LONG);
        ProductServiceImpl.forList(p);
        assertThat(p.getDescription().length()).isLessThanOrEqualTo(ProductServiceImpl.LIST_DESCRIPTION_CHARS + 1);
        assertThat(p.getName()).isEqualTo("Press brake");
    }

    @Test
    void factoryListDropsCertificatesAndLongText() {
        ManufacturerResponse m = new ManufacturerResponse();
        m.setDescription(LONG);
        m.setCertificates(List.of(new seekfactory.axoraa.dto.common.FactoryCertificate()));
        ManufacturerServiceImpl.forList(m);
        assertThat(m.getCertificates()).isEmpty();
        assertThat(m.getDescription().length()).isLessThanOrEqualTo(ManufacturerServiceImpl.LIST_DESCRIPTION_CHARS + 1);
    }
}
