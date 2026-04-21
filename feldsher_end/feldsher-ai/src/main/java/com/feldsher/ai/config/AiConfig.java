package com.feldsher.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        log.info("初始化 ChatClient");
        return ChatClient.builder(chatModel).build();
    }
}
