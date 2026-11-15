package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import seekfactory.axoraa.repository.CategoryRepository;

import java.lang.reflect.Field;
import java.util.List;

import static org.mockito.Mockito.*;

class CategoryCacheIsolationTest {
    private static void expire(CategoryServiceImpl service, String fieldName) throws Exception {
        Field field = CategoryServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Object entry = field.get(service);
        var constructor = entry.getClass().getDeclaredConstructor(List.class, long.class);
        constructor.setAccessible(true);
        field.set(service, constructor.newInstance(List.of(), System.currentTimeMillis() - 660_000));
    }

    @Test
    void refreshingRootsDoesNotRenewExpiredAllCategories() throws Exception {
        var repository = mock(CategoryRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        when(repository.findByParentIsNullOrderByNameAsc()).thenReturn(List.of());
        var service = new CategoryServiceImpl(repository, new ModelMapper());
        service.listAll();
        expire(service, "allCache");
        service.listRoots();
        service.listAll();
        verify(repository, times(2)).findAll();
    }

    @Test
    void refreshingAllCategoriesDoesNotRenewExpiredRoots() throws Exception {
        var repository = mock(CategoryRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        when(repository.findByParentIsNullOrderByNameAsc()).thenReturn(List.of());
        var service = new CategoryServiceImpl(repository, new ModelMapper());
        service.listRoots();
        expire(service, "rootsCache");
        service.listAll();
        service.listRoots();
        verify(repository, times(2)).findByParentIsNullOrderByNameAsc();
    }

    @Test
    void freshEntriesStillAvoidRepeatedDatabaseQueries() {
        var repository = mock(CategoryRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        when(repository.findByParentIsNullOrderByNameAsc()).thenReturn(List.of());
        var service = new CategoryServiceImpl(repository, new ModelMapper());
        service.listAll();
        service.listRoots();
        service.listAll();
        service.listRoots();
        verify(repository, times(1)).findAll();
        verify(repository, times(1)).findByParentIsNullOrderByNameAsc();
    }
}
