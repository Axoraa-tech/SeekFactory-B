package seekfactory.axoraa.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import seekfactory.axoraa.config.JwtAuthenticationFilter;
import seekfactory.axoraa.config.SecurityConfig;
import seekfactory.axoraa.dto.Response.media.MediaUploadResponse;
import seekfactory.axoraa.services.services.MediaStorageService;
import seekfactory.axoraa.services.services.PresenceService;
import seekfactory.axoraa.utils.JwtTokenProvider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercise the real security filter chain without application startup, a database, or object storage. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = MediaUploadSecurityTest.Config.class)
class MediaUploadSecurityTest {
    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class Config {
        @Bean MediaStorageService storage() { return mock(MediaStorageService.class); }
        @Bean JwtTokenProvider tokens() { return mock(JwtTokenProvider.class); }
        @Bean PresenceService presence() { return mock(PresenceService.class); }
        @Bean JwtAuthenticationFilter jwtFilter(JwtTokenProvider tokens, PresenceService presence) { return new JwtAuthenticationFilter(tokens, presence); }
        @Bean MediaController controller(MediaStorageService storage, JwtTokenProvider tokens) { return new MediaController(storage, tokens); }
    }

    @Autowired WebApplicationContext context;
    @Autowired MediaStorageService storage;
    MockMvc mvc;
    final MockMultipartFile scan = new MockMultipartFile("file", "scan.png", "image/png", new byte[]{1});

    @BeforeEach
    void setUp() {
        reset(storage);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(storage.store(any(), eq("document"))).thenReturn(MediaUploadResponse.builder()
                .url("/api/v1/media/11111111-1111-1111-1111-111111111111.png").contentType("image/png").size(1).build());
    }

    @Test
    void anonymousDocumentUploadIsRejectedBeforeStorage() throws Exception {
        mvc.perform(multipart("/api/v1/media").file(scan).param("kind", "document")).andExpect(status().isUnauthorized());
        verifyNoInteractions(storage);
    }

    @Test
    void authenticatedBuyerCanUploadPublicDocuments() throws Exception {
        mvc.perform(multipart("/api/v1/media").file(scan).param("kind", "document")
                .with(user("buyer").authorities(new SimpleGrantedAuthority("ROLE_BUYER")))).andExpect(status().isCreated());
        verify(storage).store(any(), eq("document"));
    }

    @Test
    void factoryUploadStillRequiresSupplierRole() throws Exception {
        mvc.perform(multipart("/api/v1/factory/media").file(scan).param("kind", "document")
                .with(user("buyer").authorities(new SimpleGrantedAuthority("ROLE_BUYER")))).andExpect(status().isForbidden());
        verifyNoInteractions(storage);
        mvc.perform(multipart("/api/v1/factory/media").file(scan).param("kind", "document")
                .with(user("supplier").authorities(new SimpleGrantedAuthority("ROLE_SUPPLIER")))).andExpect(status().isCreated());
        verify(storage).store(any(), eq("document"));
    }
}
