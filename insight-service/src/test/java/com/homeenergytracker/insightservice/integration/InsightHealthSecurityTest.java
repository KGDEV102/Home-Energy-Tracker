package com.homeenergytracker.insightservice.integration;

import com.homeenergytracker.insightservice.client.UsageServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "internal.usage-service-token=test-internal-token",
        "spring.ai.openai.api-key=test-groq-api-key"
})
@AutoConfigureMockMvc
class InsightHealthSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ChatClient chatClient;

    @MockitoBean
    private UsageServiceClient usageServiceClient;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void applicationEndpointStillRequiresJwt() throws Exception {
        mockMvc.perform(get("/api/v1/insights/not-configured"))
                .andExpect(status().isUnauthorized());
    }
}
