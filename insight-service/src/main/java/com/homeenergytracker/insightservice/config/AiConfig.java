package com.homeenergytracker.insightservice.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    ChatClient insightChatClient(
            ChatClient.Builder builder,
            @Value("${spring.ai.openai.api-key}") String apiKey
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                    "GROQ_API_KEY must not be blank"
            );
        }
        return builder.build();
    }
}
