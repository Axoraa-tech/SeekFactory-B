package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.repository.CategoryRepository;
import seekfactory.axoraa.repository.ProductRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.*;

class CategoryOrderTest {

    private static Category category(String id, Category parent) {
        var c = new Category();
        c.setId(id); c.setName(id); c.setSlug(id); c.setParent(parent);
        return c;
    }

    @Test
    void savesTheAdminOrderAndClearsSiblingsLeftOut() {
        var repository = mock(CategoryRepository.class);
        var a = category("a", null);
        var b = category("b", null);
        var c = category("c", null);
        c.setDisplayOrder(0);
        var child = category("child", a);
        child.setDisplayOrder(3);
        when(repository.findAllById(anyIterable())).thenReturn(List.of(a, b));
        when(repository.findAll()).thenReturn(List.of(a, b, c, child));
        var service = new CategoryServiceImpl(repository, new ModelMapper(), mock(ProductRepository.class));

        var result = service.reorder(List.of("b", "a"));

        assertEquals(List.of("b", "a"), result.stream().map(r -> r.getId()).toList());
        assertEquals(0, b.getDisplayOrder());
        assertEquals(1, a.getDisplayOrder());
        assertNull(c.getDisplayOrder());            // same level, left out
        assertEquals(3, child.getDisplayOrder());   // other level untouched
        assertEquals(0, result.get(0).getDisplayOrder());
    }

    @Test
    void rejectsMixedLevelsUnknownAndDuplicates() {
        var repository = mock(CategoryRepository.class);
        var root = category("root", null);
        var child = category("child", root);
        var service = new CategoryServiceImpl(repository, new ModelMapper(), mock(ProductRepository.class));

        when(repository.findAllById(anyIterable())).thenReturn(List.of(root, child));
        assertThrows(BadRequestException.class, () -> service.reorder(List.of("root", "child")));

        when(repository.findAllById(anyIterable())).thenReturn(List.of(root));
        assertThrows(BadRequestException.class, () -> service.reorder(List.of("root", "missing")));
        assertThrows(BadRequestException.class, () -> service.reorder(List.of("root", "root")));
        assertThrows(BadRequestException.class, () -> service.reorder(List.of()));
        verify(repository, never()).saveAll(anyIterable());
    }
}
