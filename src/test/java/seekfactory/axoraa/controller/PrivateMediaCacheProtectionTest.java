package seekfactory.axoraa.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import seekfactory.axoraa.services.services.ConversationService;
import seekfactory.axoraa.services.services.MediaStorageService;
import seekfactory.axoraa.services.services.SseService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrivateMediaCacheProtectionTest {
    @AfterEach
    void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test
    void participantRedirectRemainsNoStoreAndChecksAccessBeforeSigning() {
        var conversations = mock(ConversationService.class);
        var storage = mock(MediaStorageService.class);
        var controller = new ConversationController(conversations, mock(SseService.class), storage);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("buyer", "unused", List.of()));
        when(conversations.loadAttachment("conversation", "buyer", "proof.pdf"))
                .thenReturn(new ByteArrayResource(new byte[]{1}));
        when(storage.privateUrl("conversation", "proof.pdf")).thenReturn(Optional.of("https://bucket.example/private/proof"));
        assertThat(controller.downloadAttachment("conversation", "proof.pdf").getHeaders().getCacheControl())
                .isEqualTo("no-store");
        var order = inOrder(conversations, storage);
        order.verify(conversations).loadAttachment("conversation", "buyer", "proof.pdf");
        order.verify(storage).privateUrl("conversation", "proof.pdf");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("other-user", "unused", List.of()));
        when(conversations.loadAttachment("conversation", "other-user", "proof.pdf"))
                .thenThrow(new IllegalStateException("not a participant"));
        clearInvocations(storage);
        assertThatThrownBy(() -> controller.downloadAttachment("conversation", "proof.pdf"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(storage);
    }
}
