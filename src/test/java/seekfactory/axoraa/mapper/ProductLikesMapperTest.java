package seekfactory.axoraa.mapper;

import org.junit.jupiter.api.Test;
import seekfactory.axoraa.config.ModelMapperConfig;
import seekfactory.axoraa.entity.*;
import seekfactory.axoraa.repository.*;
import seekfactory.axoraa.repository.Reels.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductLikesMapperTest {
    final ProductLikeRepository likes = mock(ProductLikeRepository.class);
    final CatalogMapper mapper = new CatalogMapper(new ModelMapperConfig().modelMapper(), mock(ReelLikeRepository.class),
            mock(ReelSaveRepository.class), mock(ProductSaveRepository.class), likes, mock(ManufacturerFollowRepository.class));

    private static Product product(String id) {
        Manufacturer factory = Manufacturer.builder().name("Factory").build(); factory.setId("factory");
        Product product = Product.builder().name(id).manufacturer(factory).build(); product.setId(id);
        return product;
    }

    @Test
    void batchesRealCountsAndViewerStateAndDoesNotLeakThemToAnotherViewer() {
        when(likes.countForProducts(List.of("p1", "p2"))).thenReturn(java.util.Collections.singletonList(new Object[]{"p1", 7L}));
        when(likes.findLikedProductIds("first", List.of("p1", "p2"))).thenReturn(List.of("p1"));
        var products = List.of(product("p1"), product("p2"));
        var first = mapper.toProducts(products, "first");
        assertThat(first.getFirst().getLikesCount()).isEqualTo(7L);
        assertThat(first.getFirst().getLikedByMe()).isTrue();
        assertThat(first.get(1).getLikesCount()).isZero();
        assertThat(first.get(1).getLikedByMe()).isFalse();
        verify(likes).countForProducts(List.of("p1", "p2"));
        assertThat(mapper.toProducts(products, "second").getFirst().getLikedByMe()).isFalse();
    }

    @Test
    void guestsReceiveCountsButNoViewerLikeAndEmptyListsMakeNoQueries() {
        assertThat(mapper.toProducts(List.of(), null)).isEmpty();
        verifyNoInteractions(likes);
        var guest = mapper.toProducts(List.of(product("p1")), null).getFirst();
        assertThat(guest.getLikesCount()).isZero();
        assertThat(guest.getLikedByMe()).isNull();
        verify(likes, never()).findLikedProductIds(any(), any());
    }
}
