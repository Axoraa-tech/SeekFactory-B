package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.repository.CategoryRepository;
import seekfactory.axoraa.repository.ProductRepository;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CategoryCacheIsolationTest {
    @Test
    void newlyAddedRootIsVisibleOnNextRequest() {
        var repository = mock(CategoryRepository.class);
        var added = new Category();
        added.setId("new-root"); added.setName("New root"); added.setSlug("new-root");
        when(repository.findByParentIsNullOrderByNameAsc()).thenReturn(List.of(), List.of(added));
        var service = new CategoryServiceImpl(repository, new ModelMapper(), mock(ProductRepository.class));
        assertTrue(service.listRoots().isEmpty());
        assertEquals("new-root", service.listRoots().get(0).getId());
        verify(repository, times(2)).findByParentIsNullOrderByNameAsc();
    }
    @Test
    void newlyAddedChildIsVisibleWithoutRestartOrCacheExpiry() {
        var repository = mock(CategoryRepository.class);
        var root = new Category(); root.setId("root"); root.setName("Root"); root.setSlug("root");
        var child = new Category(); child.setId("child"); child.setName("Child"); child.setSlug("child"); child.setParent(root);
        when(repository.findAll()).thenReturn(List.of(root), List.of(root, child));
        var service = new CategoryServiceImpl(repository, new ModelMapper(), mock(ProductRepository.class));
        assertEquals(1, service.listAll().size());
        var all = service.listAll();
        assertEquals(2, all.size());
        assertEquals("root", all.get(1).getParentId());
        verify(repository, times(2)).findAll();
    }

    @Test
    void countsReflectPublicProductsAndRollUpDescendantsWithoutSiblingLeakage() {
        var repository = mock(CategoryRepository.class);
        var products = mock(ProductRepository.class);
        var root = category("root", null);
        var child = category("child", root);
        var leaf = category("leaf", child);
        var sibling = category("sibling", root);
        when(repository.findAll()).thenReturn(List.of(root, child, leaf, sibling));
        when(repository.findByParentIsNullOrderByNameAsc()).thenReturn(List.of(root));
        when(repository.findById("child")).thenReturn(java.util.Optional.of(child));
        when(repository.findByParentIdOrderByNameAsc("child")).thenReturn(List.of(leaf));
        when(repository.findBySlug("leaf")).thenReturn(java.util.Optional.of(leaf));
        when(products.countVisibleByCategory()).thenReturn(List.of(count("root", 1), count("leaf", 2), count("sibling", 4)));
        var service = new CategoryServiceImpl(repository, new ModelMapper(), products);
        var all = service.listAll();
        assertEquals(List.of(7, 2, 2, 4), all.stream().map(r -> r.getListingCount()).toList());
        assertEquals(7, service.listRoots().get(0).getListingCount());
        assertEquals(2, service.listChildren("child").get(0).getListingCount());
        assertEquals(2, service.getBySlug("leaf").getListingCount());
        // Product pause/delete/unverify removes it from repository aggregate on the next request.
        when(products.countVisibleByCategory()).thenReturn(List.of());
        assertTrue(service.listAll().stream().allMatch(r -> r.getListingCount() == 0));
        verify(products, times(5)).countVisibleByCategory();
    }

    @Test
    void aggregateQueryUsesExactPublicCatalogueVisibility() throws Exception {
        String query = ProductRepository.class.getMethod("countVisibleByCategory")
                .getAnnotation(org.springframework.data.jpa.repository.Query.class).value();
        assertTrue(query.contains("p.isActive = true AND p.listed = true AND p.manufacturer.verified = true"));
        assertTrue(query.contains("GROUP BY p.category.id"));
    }

    private Category category(String id, Category parent) {
        var category = new Category();
        category.setId(id); category.setSlug(id); category.setName(id);
        category.setParent(parent); category.setListingCount(999);
        return category;
    }

    private ProductRepository.CategoryListingCount count(String id, long count) {
        return new ProductRepository.CategoryListingCount() {
            public String getCategoryId() { return id; }
            public long getListingCount() { return count; }
        };
    }
}
