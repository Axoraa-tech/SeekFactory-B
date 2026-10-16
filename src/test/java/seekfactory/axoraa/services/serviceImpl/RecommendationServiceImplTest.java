package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.Test;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.enums.ProductStockStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationServiceImplTest {

    private static Manufacturer factory(String id) {
        Manufacturer m = Manufacturer.builder().name(id).build();
        m.setId(id);
        return m;
    }

    private static Category category(String id) {
        Category c = Category.builder().name(id).build();
        c.setId(id);
        return c;
    }

    private static Product product(String id, Manufacturer m, Category c) {
        Product p = Product.builder().name(id).manufacturer(m).category(c).build();
        p.setId(id);
        return p;
    }

    private static List<String> ids(List<Product> products) {
        return products.stream().map(Product::getId).toList();
    }

    private final Manufacturer f1 = factory("f1");
    private final Manufacturer f2 = factory("f2");
    private final Manufacturer f3 = factory("f3");
    private final Category lathes = category("lathes");
    private final Category mills = category("mills");

    @Test
    void searchMatchesFirstThenCategoriesRoundRobinThenTrending() {
        Product searched = product("searched", f1, lathes);
        Product lathe = product("lathe", f2, lathes);
        Product mill = product("mill", f3, mills);
        Product lathe2 = product("lathe2", f3, lathes);
        Product trending = product("trending", f2, mills);

        List<Product> result = RecommendationServiceImpl.assemble(
                List.of(searched),
                List.of("lathes", "mills"),
                List.of(lathe, lathe2, mill),
                List.of(trending, lathe),
                null, 60);

        // Round-robin: best category's first, the next category's first, then the best category's second
        assertThat(ids(result)).containsExactly("searched", "lathe", "mill", "lathe2", "trending");
    }

    @Test
    void leavesOutTheViewersOwnProductsAndDuplicates() {
        Product mine = product("mine", f1, lathes);
        Product other = product("other", f2, lathes);

        List<Product> result = RecommendationServiceImpl.assemble(
                List.of(mine, other), List.of("lathes"), List.of(mine, other), List.of(other, mine), "f1", 60);

        assertThat(ids(result)).containsExactly("other");
    }

    @Test
    void fallsBackToTrendingWithoutAnySignals() {
        Product a = product("a", f1, lathes);
        Product b = product("b", f2, mills);

        assertThat(ids(RecommendationServiceImpl.assemble(List.of(), List.of(), List.of(), List.of(a, b), null, 60)))
                .containsExactly("a", "b");
    }

    @Test
    void outOfStockGoesLastAndTheLimitHolds() {
        Product gone = product("gone", f1, lathes);
        gone.setStockStatus(ProductStockStatus.OUT_OF_STOCK);
        Product a = product("a", f2, lathes);
        Product b = product("b", f3, lathes);

        assertThat(ids(RecommendationServiceImpl.assemble(List.of(gone, a, b), List.of(), List.of(), List.of(), null, 60)))
                .containsExactly("a", "b", "gone");
        assertThat(RecommendationServiceImpl.assemble(List.of(gone, a, b), List.of(), List.of(), List.of(), null, 2))
                .hasSize(2);
    }

    @Test
    void noFactoryMoreThanTwiceInARowWhenAnotherCanStepIn() {
        List<Product> input = new ArrayList<>(Arrays.asList(
                product("a1", f1, lathes), product("a2", f1, lathes), product("a3", f1, lathes),
                product("a4", f1, lathes), product("b1", f2, lathes)));

        assertThat(ids(RecommendationServiceImpl.spreadFactories(input)))
                .containsExactly("a1", "a2", "b1", "a3", "a4");
    }

    @Test
    void cleansSearchTerms() {
        assertThat(RecommendationServiceImpl.cleanTerms(Arrays.asList("  CNC   lathe ", "cnc lathe", "x", null, "pumps", "a", "b1", "c1", "d1", "e1")))
                .containsExactly("CNC lathe", "pumps", "b1", "c1", "d1");
        assertThat(RecommendationServiceImpl.cleanTerms(null)).isEmpty();
    }
}
