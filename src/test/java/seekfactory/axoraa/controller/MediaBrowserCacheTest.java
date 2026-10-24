package seekfactory.axoraa.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import seekfactory.axoraa.services.services.MediaStorageService;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MVC + security chain, with no database or bucket traffic. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = MediaUploadSecurityTest.Config.class)
class MediaBrowserCacheTest {
    static final String KEY = "11111111-1111-1111-1111-111111111111.jpg";
    static final String PATH = "/api/v1/media/";
    @Autowired WebApplicationContext context;
    @Autowired MediaStorageService storage;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(storage);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(storage.publicUrl(anyString())).thenReturn(Optional.of("https://bucket.example/media/" + KEY));
        when(storage.publicRedirectMaxAgeSeconds()).thenReturn(300L);
    }

    @Test
    void guestAndSignedInPublicRedirectsHaveTheSameBrowserOnlyLifetime() throws Exception {
        mvc.perform(get(PATH + KEY)).andExpect(status().isFound())
                .andExpect(header().string("Cache-Control", "max-age=300, must-revalidate, private"));
        mvc.perform(get(PATH + KEY).with(user("buyer"))).andExpect(status().isFound())
                .andExpect(header().string("Cache-Control", "max-age=300, must-revalidate, private"));
        verify(storage, never()).load(anyString());
        verify(storage, never()).privateUrl(anyString(), anyString());
    }

    @Test
    void processingVideoAndDocumentsKeepRevalidating() throws Exception {
        String video = KEY.replace("jpg", "mp4");
        when(storage.isProcessing(video)).thenReturn(true);
        mvc.perform(get(PATH + video)).andExpect(header().string("Cache-Control", "no-cache"));
        when(storage.isProcessing(video)).thenReturn(false);
        mvc.perform(get(PATH + video)).andExpect(header().string("Cache-Control", "max-age=300, must-revalidate, private"));
        mvc.perform(get(PATH + KEY.replace("jpg", "pdf"))).andExpect(header().string("Cache-Control", "no-cache"));
    }

    @Test
    void storageWithoutASafeLifetimeKeepsRevalidating() throws Exception {
        when(storage.publicRedirectMaxAgeSeconds()).thenReturn(0L);
        mvc.perform(get(PATH + KEY)).andExpect(header().string("Cache-Control", "no-cache"));
    }

    @Test
    void replacementUsesItsNewKeyAndLocation() throws Exception {
        String replacement = KEY.replace("11111111", "22222222");
        when(storage.publicUrl(replacement)).thenReturn(Optional.of("https://bucket.example/media/" + replacement));
        mvc.perform(get(PATH + replacement)).andExpect(redirectedUrl("https://bucket.example/media/" + replacement));
    }

    @Test
    void localBodiesKeepTheirExistingImmutablePolicy() throws Exception {
        when(storage.publicUrl(KEY)).thenReturn(Optional.empty());
        when(storage.load(KEY)).thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));
        when(storage.contentTypeOf(KEY)).thenReturn("image/jpeg");
        mvc.perform(get(PATH + KEY)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"));
        when(storage.isProcessing(KEY)).thenReturn(true);
        mvc.perform(get(PATH + KEY)).andExpect(header().string("Cache-Control", "no-cache"));
    }

    @Test
    void privateAndSessionRequestsRemainUnauthorizedAndUncacheable() throws Exception {
        mvc.perform(get("/api/v1/conversations/conv-1/attachments/" + KEY)).andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"));
        mvc.perform(post("/api/v1/media/upload-token")).andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"));
        verifyNoInteractions(storage);
    }
}
