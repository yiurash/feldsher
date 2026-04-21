package com.feldsher.ai.entity;

import com.feldsher.ai.enums.AgentType;
import com.feldsher.ai.enums.ConversationPhase;
import com.feldsher.ai.enums.IntentType;
import com.feldsher.ai.enums.SkillType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationContext {

    private String conversationId;
    private ConversationPhase currentPhase;
    private IntentType currentIntent;
    private AgentType currentAgent;
    private SkillType currentSkill;
    private List<Message> messages;
    private CollectedInfo collectedInfo;
    private Map<String, Object> metadata;
    private LocalDateTime startTime;
    private LocalDateTime lastActiveTime;
    private boolean isActive;
    private int questionCount;
    private List<String> pendingQuestions;

    public void addMessage(Message message) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(message);
        lastActiveTime = LocalDateTime.now();
    }

    public void addMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }

    public Object getMetadata(String key) {
        if (metadata == null) {
            return null;
        }
        return metadata.get(key);
    }

    public void addPendingQuestion(String question) {
        if (pendingQuestions == null) {
            pendingQuestions = new ArrayList<>();
        }
        pendingQuestions.add(question);
    }

    public void incrementQuestionCount() {
        questionCount++;
    }
}
