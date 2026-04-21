package com.feldsher.ai.controller;

import cn.hutool.core.util.IdUtil;
import com.feldsher.ai.dto.ChatRequest;
import com.feldsher.ai.entity.ConversationContext;
import com.feldsher.ai.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Tag(name = "AI对话接口", description = "医疗AI助手对话接口")
@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    @Operation(summary = "创建新对话", description = "创建一个新的对话会话")
    @PostMapping("/new")
    public Map<String, String> createConversation() {
        ConversationContext context = chatService.createConversation();
        return Map.of(
                "conversationId", context.getConversationId(),
                "status", "created"
        );
    }

    @Operation(summary = "SSE流式对话", description = "通过SSE进行流式对话，支持实时输出")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestParam(value = "conversationId", required = false) String conversationId,
            @RequestParam("message") String message) {
        
        log.info("SSE对话请求，conversationId: {}, message: {}", conversationId, message);
        
        if (conversationId == null || conversationId.isEmpty()) {
            ConversationContext context = chatService.createConversation();
            conversationId = context.getConversationId();
        }
        
        String finalConversationId = conversationId;
        
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.put(finalConversationId, emitter);
        
        emitter.onCompletion(() -> {
            log.info("SSE连接完成: {}", finalConversationId);
            emitters.remove(finalConversationId);
        });
        
        emitter.onTimeout(() -> {
            log.info("SSE连接超时: {}", finalConversationId);
            emitters.remove(finalConversationId);
            try {
                emitter.send(SseEmitter.event().name("timeout").data("连接超时"));
            } catch (IOException e) {
                log.error("发送超时消息失败: {}", e.getMessage());
            }
        });
        
        emitter.onError(e -> {
            log.error("SSE连接错误: {}, 错误: {}", finalConversationId, e.getMessage());
            emitters.remove(finalConversationId);
        });
        
        executor.execute(() -> {
            try {
                emitter.send(SseEmitter.event().name("start").data("开始处理..."));
                
                ChatService.ChatResult result = chatService.processMessage(finalConversationId, message);
                
                String response = result.getResponse();
                if (response != null && !response.isEmpty()) {
                    for (int i = 0; i < response.length(); i += 5) {
                        int end = Math.min(i + 5, response.length());
                        String chunk = response.substring(i, end);
                        
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(chunk));
                        
                        Thread.sleep(50);
                    }
                }
                
                emitter.send(SseEmitter.event()
                        .name("info")
                        .data(String.format("{\"conversationId\":\"%s\",\"isComplete\":%s,\"phase\":\"%s\"}",
                                result.getConversationId(),
                                result.isComplete(),
                                result.getPhase() != null ? result.getPhase() : "ongoing")));
                
                emitter.send(SseEmitter.event().name("done").data("完成"));
                emitter.complete();
                
            } catch (Exception e) {
                log.error("处理SSE消息失败: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("处理失败: " + e.getMessage()));
                    emitter.completeWithError(e);
                } catch (IOException ex) {
                    log.error("发送错误消息失败: {}", ex.getMessage());
                }
            }
        });
        
        return emitter;
    }

    @Operation(summary = "普通对话接口", description = "非流式对话接口，一次返回完整结果")
    @PostMapping("/send")
    public Map<String, Object> sendMessage(@RequestBody ChatRequest request) {
        log.info("普通对话请求，conversationId: {}, message: {}", 
                request.getConversationId(), request.getMessage());
        
        String conversationId = request.getConversationId();
        
        if (conversationId == null || conversationId.isEmpty()) {
            ConversationContext context = chatService.createConversation();
            conversationId = context.getConversationId();
        }
        
        ChatService.ChatResult result = chatService.processMessage(conversationId, request.getMessage());
        
        return Map.of(
                "conversationId", result.getConversationId(),
                "response", result.getResponse(),
                "isComplete", result.isComplete(),
                "isEmergency", result.isEmergency(),
                "phase", result.getPhase() != null ? result.getPhase() : "ongoing",
                "progress", result.getProgress() != null ? result.getProgress() : 0.0
        );
    }

    @Operation(summary = "获取对话状态", description = "获取指定对话的当前状态")
    @GetMapping("/status/{conversationId}")
    public Map<String, Object> getConversationStatus(@PathVariable String conversationId) {
        ConversationContext context = chatService.getContext(conversationId);
        
        if (context == null) {
            return Map.of(
                    "error", "对话不存在",
                    "conversationId", conversationId
            );
        }
        
        return Map.of(
                "conversationId", context.getConversationId(),
                "phase", context.getCurrentPhase() != null ? context.getCurrentPhase().getCode() : "unknown",
                "phaseDescription", context.getCurrentPhase() != null ? context.getCurrentPhase().getDescription() : "未知",
                "intent", context.getCurrentIntent() != null ? context.getCurrentIntent().getDescription() : "未识别",
                "currentAgent", context.getCurrentAgent() != null ? context.getCurrentAgent().getName() : "无",
                "currentSkill", context.getCurrentSkill() != null ? context.getCurrentSkill().getName() : "无",
                "questionCount", context.getQuestionCount(),
                "isActive", context.isActive(),
                "messageCount", context.getMessages() != null ? context.getMessages().size() : 0
        );
    }

    @Operation(summary = "结束对话", description = "主动结束指定对话")
    @PostMapping("/end/{conversationId}")
    public Map<String, Object> endConversation(@PathVariable String conversationId) {
        chatService.endConversation(conversationId);
        return Map.of(
                "conversationId", conversationId,
                "status", "ended"
        );
    }

    @Operation(summary = "清除对话", description = "清除指定对话的上下文")
    @DeleteMapping("/clear/{conversationId}")
    public Map<String, Object> clearConversation(@PathVariable String conversationId) {
        chatService.clearContext(conversationId);
        return Map.of(
                "conversationId", conversationId,
                "status", "cleared"
        );
    }
}
