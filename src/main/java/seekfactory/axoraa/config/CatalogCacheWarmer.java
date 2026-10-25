package seekfactory.axoraa.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import seekfactory.axoraa.dto.Response.category.CategoryResponse;
import seekfactory.axoraa.services.services.CategoryService;
import seekfactory.axoraa.services.services.ManufacturerService;
import seekfactory.axoraa.services.services.ProductService;
import seekfactory.axoraa.services.services.ReelService;
import seekfactory.axoraa.enums.FeedTab;

/**
 * Builds the costly public catalogue lists right after startup (all approved factories, the full
 * product catalogue, each top-level category's products and the first "For You" page), in the background, so
 * the first visitors after a deploy or restart don't wait for them. From then on those caches
 * rebuild themselves in the background.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogCacheWarmer {

    private final ManufacturerService manufacturerService;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final ReelService reelService;

    @EventListener(ApplicationReadyEvent.class)
    public void warm() {
        Thread.ofVirtual().name("catalog-cache-warmer").start(() -> {
            long started = System.currentTimeMillis();
            try {
                manufacturerService.listAll();
                productService.listCatalog(null);
                // First "For You" page, as the web feed asks for it (50 per page)
                reelService.getFeed(FeedTab.FOR_YOU, null, 0, 50);
                int categories = 0;
                for (CategoryResponse root : categoryService.listRoots()) {
                    productService.listByCategory(root.getId(), null);
                    categories++;
                }
                log.info("Catalogue caches ready ({} categories) in {} ms", categories, System.currentTimeMillis() - started);
            } catch (RuntimeException e) {
                // Only a head start: requests still build whatever is missing
                log.warn("Catalogue cache warm-up stopped: {}", e.getMessage());
            }
        });
    }
}
