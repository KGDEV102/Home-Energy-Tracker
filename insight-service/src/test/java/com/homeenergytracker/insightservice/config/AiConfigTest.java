package com.homeenergytracker.insightservice.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiConfigTest {

    @Mock
    private ChatClient.Builder builder;

    @Mock
    private ChatClient chatClient;

    private final AiConfig aiConfig = new AiConfig();

    @Test
    void insightChatClient_shouldBuildClientForConfiguredApiKey() {
        when(builder.build()).thenReturn(chatClient);

        ChatClient result = aiConfig.insightChatClient(builder, "test-api-key");

        assertSame(chatClient, result);
        verify(builder).build();
    }

    @Test
    void insightChatClient_shouldRejectBlankApiKey() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> aiConfig.insightChatClient(builder, "  ")
        );

        assertEquals("GROQ_API_KEY must not be blank", exception.getMessage());
    }

    @Test
    void insightChatClient_shouldRejectNullApiKey() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> aiConfig.insightChatClient(builder, null)
        );

        assertEquals("GROQ_API_KEY must not be blank", exception.getMessage());
    }
}
