package seekfactory.axoraa.config;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import seekfactory.axoraa.services.services.PresenceService;
import seekfactory.axoraa.utils.JwtTokenProvider;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes=ChatAsyncSecurityTest.Config.class)
class ChatAsyncSecurityTest {
    @Configuration @EnableWebMvc @Import(SecurityConfig.class)
    static class Config {
        @Bean JwtAuthenticationFilter jwtFilter() {return new JwtAuthenticationFilter(mock(JwtTokenProvider.class),mock(PresenceService.class));}
        @Bean Endpoints endpoints() {return new Endpoints();}
    }
    @RestController static class Endpoints {
        @GetMapping("/api/v1/conversations/test/stream") String stream(){return "completed";}
        @GetMapping("/api/v1/conversations/test/messages") String messages(){return "private";}
    }
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    @Test void initialStreamStillRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/conversations/test/stream")).andExpect(status().isUnauthorized());
    }
    @Test void internalStreamCompletionDoesNotRequireASecondJwtFilterPass() throws Exception {
        mvc.perform(get("/api/v1/conversations/test/stream").with(r->{r.setDispatcherType(DispatcherType.ASYNC);return r;}))
                .andExpect(status().isOk());
    }
    @Test void otherAsyncPrivatePathsRemainProtected() throws Exception {
        mvc.perform(get("/api/v1/conversations/test/messages").with(r->{r.setDispatcherType(DispatcherType.ASYNC);return r;}))
                .andExpect(status().isUnauthorized());
    }
}
