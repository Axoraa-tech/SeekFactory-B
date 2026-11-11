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
    final ViewEventRepository views = mock(ViewEventRepository.class);
    final CatalogMapper mapper = new CatalogMapper(new ModelMapperConfig().modelMapper(), mock(ReelLikeRepository.class),
            mock(ReelSaveRepository.class), mock(ProductSaveRepository.class), likes, mock(ManufacturerFollowRepository.class), views);

    private static Product product(String id) {
        Manufacturer factory = Manufacturer.builder().name("Factory").build(); factory.setId("factory");
        Product product = Product.builder().name(id).manufacturer(factory).build(); product.setId(id);
        return product;
    }

    @Test
    void productVideoSurvivesBuyerMappingAndCachedViewerCopies() {
        Product source = product("video-product");
        source.setVideoUrl("/api/v1/media/0b5e7d1c-2f4a-4c1e-9a3b-7d6e5f4a3b2c.mp4");
        var mapped = mapper.toProducts(List.of(source), null);
        assertThat(mapped.getFirst().getVideoUrl()).isEqualTo(source.getVideoUrl());
        assertThat(mapper.copyWithViewerState(mapped, "buyer").getFirst().getVideoUrl())
                .isEqualTo(source.getVideoUrl());
        assertThat(mapper.toProduct(product("legacy")).getVideoUrl()).isNull();
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
    void batchesViewCountsInOneQueryAndDefaultsMissingOnesToZero() {
        when(views.countByEntityIds(seekfactory.axoraa.enums.ViewEntityType.PRODUCT, List.of("p1", "p2")))
                .thenReturn(java.util.Collections.singletonList(new Object[]{"p2", 28L}));
        var products = mapper.toProducts(List.of(product("p1"), product("p2")), null);
        assertThat(products.getFirst().getViewsCount()).isZero();
        assertThat(products.get(1).getViewsCount()).isEqualTo(28L);
        verify(views).countByEntityIds(seekfactory.axoraa.enums.ViewEntityType.PRODUCT, List.of("p1", "p2"));
    }

    @Test
    void guestsReceiveCountsButNoViewerLikeAndEmptyListsMakeNoQueries() {
        assertThat(mapper.toProducts(List.of(), null)).isEmpty();
        verifyNoInteractions(likes);
        verifyNoInteractions(views);
        var guest = mapper.toProducts(List.of(product("p1")), null).getFirst();
        assertThat(guest.getLikesCount()).isZero();
        assertThat(guest.getLikedByMe()).isNull();
        verify(likes, never()).findLikedProductIds(any(), any());
    }
}
