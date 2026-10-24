package seekfactory.axoraa.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import seekfactory.axoraa.config.*;
import seekfactory.axoraa.services.services.*;
import seekfactory.axoraa.utils.JwtTokenProvider;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real existing security policy, without application startup or database access. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ProductLikeSecurityTest.Config.class)
class ProductLikeSecurityTest {
    @Configuration @EnableWebMvc @Import(SecurityConfig.class)
    static class Config {
        @Bean ProductService products() { return mock(ProductService.class); }
        @Bean RecommendationService recommendations() { return mock(RecommendationService.class); }
        @Bean JwtTokenProvider tokens() { return mock(JwtTokenProvider.class); }
        @Bean PresenceService presence() { return mock(PresenceService.class); }
        @Bean JwtAuthenticationFilter jwtFilter(JwtTokenProvider tokens, PresenceService presence) { return new JwtAuthenticationFilter(tokens, presence); }
        @Bean ProductController controller(ProductService products, RecommendationService recommendations) { return new ProductController(products, recommendations); }
    }
    @Autowired WebApplicationContext context;
    @Autowired ProductService products;
    MockMvc mvc;
    @BeforeEach void setup() {
        reset(products);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @Test void guestCannotLikeAndNeverReachesTheService() throws Exception {
        mvc.perform(post("/api/v1/products/product/like")).andExpect(status().isUnauthorized());
        verifyNoInteractions(products);
    }
    @Test void signedInBuyerUsesTheAuthenticatedIdentityAndReceivesPersistedState() throws Exception {
        when(products.toggleLike("product", "buyer-id")).thenReturn(Map.of("liked", true, "likesCount", 8L));
        var auth = new UsernamePasswordAuthenticationToken("buyer-id", null, List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
        mvc.perform(post("/api/v1/products/product/like").with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likesCount").value(8));
        verify(products).toggleLike("product", "buyer-id");
    }
}
