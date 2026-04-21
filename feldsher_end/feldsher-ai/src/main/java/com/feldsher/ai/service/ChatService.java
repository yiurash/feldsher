package com.feldsher.ai.service;

import cn.hutool.core.util.IdUtil;
import com.feldsher.ai.dto.AgentAnalysisResult;
import com.feldsher.ai.entity.CollectedInfo;
import com.feldsher.ai.entity.ConversationContext;
import com.feldsher.ai.entity.Message;
import com.feldsher.ai.enums.AgentType;
import com.feldsher.ai.enums.ConversationPhase;
import com.feldsher.ai.enums.IntentType;
import com.feldsher.ai.enums.SkillType;
import com.feldsher.ai.service.agent.CaseRetrievalAgentService;
import com.feldsher.ai.service.agent.ConditionCollectionAgentService;
import com.feldsher.ai.service.agent.MasterAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final MasterAgentService masterAgentService;
    private final ConditionCollectionAgentService conditionCollectionAgentService;
    private final CaseRetrievalAgentService caseRetrievalAgentService;
    private final SkillService skillService;

    private final Map<String, ConversationContext> contextStore = new ConcurrentHashMap<>();

    public ConversationContext createConversation() {
        String conversationId = IdUtil.simpleUUID();
        
        ConversationContext context = ConversationContext.builder()
                .conversationId(conversationId)
                .currentPhase(ConversationPhase.INITIAL)
                .messages(new ArrayList<>())
                .collectedInfo(CollectedInfo.builder().build())
                .metadata(new ConcurrentHashMap<>())
                .startTime(LocalDateTime.now())
                .lastActiveTime(LocalDateTime.now())
                .isActive(true)
                .questionCount(0)
                .pendingQuestions(new ArrayList<>())
                .build();
        
        contextStore.put(conversationId, context);
        log.info("创建新对话: {}", conversationId);
        
        return context;
    }

    public ConversationContext getContext(String conversationId) {
        return contextStore.get(conversationId);
    }

    public ChatResult processMessage(String conversationId, String userMessage) {
        log.info("处理消息，对话ID: {}, 消息: {}", conversationId, userMessage);
        
        ConversationContext context = contextStore.get(conversationId);
        if (context == null) {
            context = createConversation();
            conversationId = context.getConversationId();
        }
        
        Message userMsg = Message.builder()
                .id(IdUtil.simpleUUID())
                .conversationId(conversationId)
                .role(Message.Role.USER)
                .content(userMessage)
                .timestamp(LocalDateTime.now())
                .type(Message.MessageType.TEXT)
                .build();
        context.addMessage(userMsg);
        
        AgentAnalysisResult masterResult = masterAgentService.analyzeIntent(context, userMessage);
        
        if (Boolean.TRUE.equals(masterResult.getIsEmergency())) {
            return ChatResult.builder()
                    .conversationId(conversationId)
                    .response(masterResult.getEmergencyMessage())
                    .isComplete(true)
                    .isEmergency(true)
                    .build();
        }
        
        String intent = masterResult.getIntent();
        log.info("识别意图: {}", intent);
        
        IntentType intentType = IntentType.fromCode(intent);
        context.setCurrentIntent(intentType);
        
        switch (intentType) {
            case GREETING:
                return handleGreeting(context, masterResult);
                
            case UNCLEAR:
                return handleUnclear(context, masterResult);
                
            case CONDITION_CONSULT:
                return handleConditionConsult(context, userMessage, masterResult);
                
            case INFORMATION_CONFIRM:
                return handleInformationConfirm(context, userMessage);
                
            case CASE_QUERY:
                return handleCaseQuery(context, userMessage, masterResult);
                
            default:
                return handleUnclear(context, masterResult);
        }
    }

    private ChatResult handleGreeting(ConversationContext context, AgentAnalysisResult result) {
        log.info("处理问候意图");
        
        String response = result.getImmediateResponse();
        if (response == null) {
            response = "您好！我是您的医疗助手，很高兴为您服务。请问您有什么健康问题需要咨询吗？";
        }
        
        Message assistantMsg = Message.builder()
                .id(IdUtil.simpleUUID())
                .conversationId(context.getConversationId())
                .role(Message.Role.ASSISTANT)
                .content(response)
                .timestamp(LocalDateTime.now())
                .type(Message.MessageType.TEXT)
                .build();
        context.addMessage(assistantMsg);
        
        return ChatResult.builder()
                .conversationId(context.getConversationId())
                .response(response)
                .isComplete(false)
                .build();
    }

    private ChatResult handleUnclear(ConversationContext context, AgentAnalysisResult result) {
        log.info("处理模糊意图");
        
        String response = result.getImmediateResponse();
        if (response == null) {
            response = "抱歉，我不太明白您的问题。请问您是有身体不适的症状需要咨询，还是想了解某种疾病的相关信息呢？";
        }
        
        Message assistantMsg = Message.builder()
                .id(IdUtil.simpleUUID())
                .conversationId(context.getConversationId())
                .role(Message.Role.ASSISTANT)
                .content(response)
                .timestamp(LocalDateTime.now())
                .type(Message.MessageType.TEXT)
                .build();
        context.addMessage(assistantMsg);
        
        return ChatResult.builder()
                .conversationId(context.getConversationId())
                .response(response)
                .isComplete(false)
                .build();
    }

    private ChatResult handleConditionConsult(ConversationContext context, String userMessage, AgentAnalysisResult result) {
        log.info("处理病情咨询意图");
        
        context.setCurrentAgent(AgentType.CONDITION_COLLECTION_AGENT);
        
        if (context.getCurrentPhase() == ConversationPhase.INITIAL || 
            context.getCurrentPhase() == ConversationPhase.INTENT_ANALYSIS) {
            context.setCurrentPhase(ConversationPhase.SKILL_SELECTION);
            
            SkillType skillType = skillService.selectSkill(userMessage, null);
            context.setCurrentSkill(skillType);
            
            log.info("选择Skill: {}", skillType.getName());
        }
        
        AgentAnalysisResult ccResult = conditionCollectionAgentService.process(context, userMessage);
        
        String response = ccResult.getImmediateResponse();
        if (response == null) {
            response = ccResult.getSummary();
        }
        
        Message assistantMsg = Message.builder()
                .id(IdUtil.simpleUUID())
                .conversationId(context.getConversationId())
                .role(Message.Role.ASSISTANT)
                .content(response)
                .timestamp(LocalDateTime.now())
                .type(Message.MessageType.QUESTION)
                .build();
        context.addMessage(assistantMsg);
        
        if ("REQUEST_CASE_RETRIEVAL".equals(ccResult.getNextAction())) {
            log.info("请求病例检索");
            return handleCaseRetrievalAfterCollection(context, ccResult);
        }
        
        return ChatResult.builder()
                .conversationId(context.getConversationId())
                .response(response)
                .isComplete(false)
                .phase(context.getCurrentPhase().getCode())
                .progress(ccResult.getProgress())
                .build();
    }

    private ChatResult handleInformationConfirm(ConversationContext context, String userMessage) {
        log.info("处理信息确认意图");
        
        if (context.getCurrentAgent() == AgentType.CONDITION_COLLECTION_AGENT) {
            AgentAnalysisResult ccResult = conditionCollectionAgentService.process(context, userMessage);
            
            String response = ccResult.getImmediateResponse();
            if (response == null) {
                response = ccResult.getSummary();
            }
            
            Message assistantMsg = Message.builder()
                    .id(IdUtil.simpleUUID())
                    .conversationId(context.getConversationId())
                    .role(Message.Role.ASSISTANT)
                    .content(response)
                    .timestamp(LocalDateTime.now())
                    .type(Message.MessageType.TEXT)
                    .build();
            context.addMessage(assistantMsg);
            
            if ("REQUEST_CASE_RETRIEVAL".equals(ccResult.getNextAction())) {
                log.info("请求病例检索");
                return handleCaseRetrievalAfterCollection(context, ccResult);
            }
            
            return ChatResult.builder()
                    .conversationId(context.getConversationId())
                    .response(response)
                    .isComplete(false)
                    .phase(context.getCurrentPhase().getCode())
                    .progress(ccResult.getProgress())
                    .build();
        }
        
        return ChatResult.builder()
                .conversationId(context.getConversationId())
                .response("好的，我已记录您的信息。")
                .isComplete(false)
                .build();
    }

    private ChatResult handleCaseQuery(ConversationContext context, String userMessage, AgentAnalysisResult result) {
        log.info("处理病例查询意图");
        
        context.setCurrentAgent(AgentType.CASE_RETRIEVAL_AGENT);
        
        AgentAnalysisResult crResult = caseRetrievalAgentService.processDirectQuery(context, userMessage);
        
        String response = crResult.getSummary();
        
        Message assistantMsg = Message.builder()
                .id(IdUtil.simpleUUID())
                .conversationId(context.getConversationId())
                .role(Message.Role.ASSISTANT)
                .content(response)
                .timestamp(LocalDateTime.now())
                .type(Message.MessageType.DIAGNOSIS)
                .build();
        context.addMessage(assistantMsg);
        
        context.setCurrentPhase(ConversationPhase.TERMINATED);
        context.setActive(false);
        
        return ChatResult.builder()
                .conversationId(context.getConversationId())
                .response(response)
                .isComplete(true)
                .phase("COMPLETED")
                .build();
    }

    private ChatResult handleCaseRetrievalAfterCollection(ConversationContext context, AgentAnalysisResult ccResult) {
        log.info("完成问诊，开始病例检索");
        
        context.setCurrentAgent(AgentType.CASE_RETRIEVAL_AGENT);
        context.setCurrentPhase(ConversationPhase.CASE_RETRIEVAL);
        
        List<String> searchTerms = ccResult.getRecommendedSearchTerms();
        if (searchTerms == null || searchTerms.isEmpty()) {
            searchTerms = new ArrayList<>();
            CollectedInfo info = context.getCollectedInfo();
            if (info != null && info.getChiefComplaint() != null) {
                searchTerms.add(info.getChiefComplaint());
            }
        }
        
        StringBuilder userInputBuilder = new StringBuilder();
        CollectedInfo info = context.getCollectedInfo();
        if (info != null) {
            if (info.getChiefComplaint() != null) {
                userInputBuilder.append("主要症状：").append(info.getChiefComplaint()).append("。");
            }
            if (info.getDuration() != null) {
                userInputBuilder.append("持续时间：").append(info.getDuration()).append("。");
            }
        }
        
        String userInput = userInputBuilder.toString();
        if (userInput.isEmpty()) {
            userInput = "病情咨询";
        }
        
        AgentAnalysisResult crResult = caseRetrievalAgentService.process(
                context, userInput, searchTerms);
        
        String response = crResult.getSummary();
        
        Message assistantMsg = Message.builder()
                .id(IdUtil.simpleUUID())
                .conversationId(context.getConversationId())
                .role(Message.Role.ASSISTANT)
                .content(response)
                .timestamp(LocalDateTime.now())
                .type(Message.MessageType.SUMMARY)
                .build();
        context.addMessage(assistantMsg);
        
        context.setCurrentPhase(ConversationPhase.TERMINATED);
        context.setActive(false);
        
        return ChatResult.builder()
                .conversationId(context.getConversationId())
                .response(response)
                .isComplete(true)
                .phase("COMPLETED")
                .build();
    }

    public void endConversation(String conversationId) {
        ConversationContext context = contextStore.get(conversationId);
        if (context != null) {
            context.setActive(false);
            context.setCurrentPhase(ConversationPhase.TERMINATED);
        }
    }

    public void clearContext(String conversationId) {
        contextStore.remove(conversationId);
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ChatResult {
        private String conversationId;
        private String response;
        private boolean isComplete;
        private boolean isEmergency;
        private String phase;
        private Double progress;
    }
}
