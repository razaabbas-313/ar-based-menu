package com.atelier.media.web;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.security.SecurityConfig;
import com.atelier.media.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, TokenService.class})
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private MockMvc mvc;

    @SpyBean
    private AtelierProperties props;

    @Test
    void healthEndpointIsPubliclyAccessibleAndReturns200() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthEndpointSupportsHeadRequest() throws Exception {
        mvc.perform(head("/health"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedStudioEndpointRemainsProtectedWith401() throws Exception {
        mvc.perform(get("/api/v1/studio/menu"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedAdminEndpointRemainsProtectedWith401() throws Exception {
        mvc.perform(get("/api/v1/admin/restaurants"))
                .andExpect(status().isUnauthorized());
    }
}
