package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import seekfactory.axoraa.entity.*;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.mapper.CatalogMapper;
import seekfactory.axoraa.repository.*;
import seekfactory.axoraa.utils.CategoryTree;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductLikesTest {
    final ProductRepository products = mock(ProductRepository.class);
    final ProductLikeRepository likes = mock(ProductLikeRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final ProductServiceImpl service = new ProductServiceImpl(products, mock(ProductSaveRepository.class), likes,
            users, mock(CatalogMapper.class), mock(CategoryTree.class));
    Product product;
    User user;

    @BeforeEach
    void setup() {
        product = Product.builder().manufacturer(Manufacturer.builder().verified(true).build()).build();
        product.setId("product");
        user = User.builder().name("Buyer").build(); user.setId("buyer");
        when(products.findForLike("product")).thenReturn(Optional.of(product));
        when(users.findById("buyer")).thenReturn(Optional.of(user));
    }

    @Test
    void createsAProductLikeAndReturnsThePersistedCountAfterFlush() {
        when(likes.countByProductId("product")).thenReturn(9L);
        assertThat(service.toggleLike("product", "buyer")).containsEntry("liked", true).containsEntry("likesCount", 9L);
        var order = inOrder(products, likes);
        order.verify(products).findForLike("product");
        order.verify(likes).findByProductIdAndUserId("product", "buyer");
        order.verify(likes).save(argThat(like -> like.getProduct() == product && like.getUser() == user));
        order.verify(likes).flush();
        order.verify(likes).countByProductId("product");
    }

    @Test
    void unlikesAPausedProductAndNeverDecrementsAnInventedCounter() {
        product.setListed(false);
        ProductLike existing = ProductLike.builder().product(product).user(user).build();
        when(likes.findByProductIdAndUserId("product", "buyer")).thenReturn(Optional.of(existing));
        when(likes.countByProductId("product")).thenReturn(0L);
        assertThat(service.toggleLike("product", "buyer")).containsEntry("liked", false).containsEntry("likesCount", 0L);
        verify(likes).delete(existing);
        verify(likes, never()).save(any());
        verifyNoInteractions(users);
    }

    @Test
    void rejectsNewLikesForPausedDeletedOrUnapprovedProducts() {
        product.setListed(false);
        assertThatThrownBy(() -> service.toggleLike("product", "buyer")).isInstanceOf(ResourceNotFoundException.class);
        product.setListed(true); product.setIsActive(false);
        assertThatThrownBy(() -> service.toggleLike("product", "buyer")).isInstanceOf(ResourceNotFoundException.class);
        product.setIsActive(true); product.getManufacturer().setVerified(false);
        assertThatThrownBy(() -> service.toggleLike("product", "buyer")).isInstanceOf(ResourceNotFoundException.class);
        verify(likes, never()).save(any());
    }

    @Test
    void missingProductOrUserNeverWritesALike() {
        when(products.findForLike("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.toggleLike("missing", "buyer")).isInstanceOf(ResourceNotFoundException.class);
        when(users.findById("buyer")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.toggleLike("product", "buyer")).isInstanceOf(ResourceNotFoundException.class);
        verify(likes, never()).save(any());
    }
}
