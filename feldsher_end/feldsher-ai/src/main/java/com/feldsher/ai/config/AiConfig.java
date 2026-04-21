package com.feldsher.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AiConfig {

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:qwen-max}")
    private String model;

    @Bean
    public OpenAiApi openAiApi() {
        log.info("初始化 OpenAiApi, baseUrl: {}, model: {}", baseUrl, model);
        
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
        
        return api;
    }

    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
        log.info("初始化 OpenAiChatModel, model: {}", model);
        
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatModel.OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(0.7)
                        .maxTokens(4096)
                        .build())
                .build();
        
        return chatModel;
    }

    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        log.info("初始化 ChatClient");
        return ChatClient.builder(chatModel).build();
    }
}
