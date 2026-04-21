package com.feldsher.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    private String id;
    private String conversationId;
    private Role role;
    private String content;
    private LocalDateTime timestamp;
    private MessageType type;

    public enum Role {
        USER,
        ASSISTANT,
        SYSTEM,
        TOOL
    }

    public enum MessageType {
        TEXT,
        QUESTION,
        ANSWER,
        DIAGNOSIS,
        SUMMARY,
        ERROR
    }
}
