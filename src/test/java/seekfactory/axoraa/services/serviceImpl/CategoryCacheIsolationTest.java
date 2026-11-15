package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.repository.CategoryRepository;
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
        var service = new CategoryServiceImpl(repository, new ModelMapper());
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
        var service = new CategoryServiceImpl(repository, new ModelMapper());
        assertEquals(1, service.listAll().size());
        var all = service.listAll();
        assertEquals(2, all.size());
        assertEquals("root", all.get(1).getParentId());
        verify(repository, times(2)).findAll();
    }
}
