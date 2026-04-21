package com.feldsher.ai.service.agent;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feldsher.ai.dto.AgentAnalysisResult;
import com.feldsher.ai.entity.ConversationContext;
import com.feldsher.ai.entity.Message;
import com.feldsher.ai.enums.AgentType;
import com.feldsher.ai.enums.ConversationPhase;
import com.feldsher.ai.enums.IntentType;
import com.feldsher.ai.enums.SkillType;
import com.feldsher.ai.service.PromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MasterAgentService {

    private final ChatClient chatClient;
    private final PromptService promptService;
    private final ObjectMapper objectMapper;

    private static final List<String> EMERGENCY_KEYWORDS = List.of(
            "胸痛", "胸闷", "呼吸困难", "濒死", "大出血", "吐血", "便血",
            "晕厥", "昏迷", "抽搐", "窒息", "猝死", "急性", "剧痛",
            "心肌梗死", "心梗", "中风", "脑梗", "脑出血"
    );

    public AgentAnalysisResult analyzeIntent(ConversationContext context, String userInput) {
        log.info("主Agent开始分析意图，用户输入: {}", userInput);
        
        if (checkEmergency(userInput)) {
            return AgentAnalysisResult.builder()
                    .isEmergency(true)
                    .emergencyMessage("根据您描述的症状，情况可能比较紧急。强烈建议您立即前往医院就诊，或拨打急救电话。")
                    .intent(IntentType.UNCLEAR.getCode())
                    .nextAction("EMERGENCY")
                    .build();
        }
        
        if (context.getCurrentPhase() == ConversationPhase.QUESTIONING) {
            return AgentAnalysisResult.builder()
                    .intent(IntentType.INFORMATION_CONFIRM.getCode())
                    .confidence(0.9)
                    .targetAgent(context.getCurrentAgent() != null ? context.getCurrentAgent().getCode() : null)
                    .targetSkill(context.getCurrentSkill() != null ? context.getCurrentSkill().getCode() : null)
                    .reasoning("用户正在回答问诊问题，继续当前对话流程")
                    .nextAction("CONTINUE_QUESTIONING")
                    .build();
        }
        
        String systemPrompt = buildSystemPrompt();
        String userMessage = buildUserMessage(context, userInput);
        
        try {
            ChatResponse response = chatClient.prompt()
                    .messages(
                            new SystemMessage(systemPrompt),
                            new UserMessage(userMessage)
                    )
                    .call()
                    .chatResponse();
            
            String content = response.getResult().getOutput().getText();
            log.info("主Agent分析结果: {}", content);
            
            AgentAnalysisResult result = parseAnalysisResult(content);
            
            if (result.getIntent() == null) {
                result = handleUnclearIntent(userInput);
            }
            
            return result;
        } catch (Exception e) {
            log.error("主Agent分析失败: {}", e.getMessage(), e);
            return handleUnclearIntent(userInput);
        }
    }

    private boolean checkEmergency(String userInput) {
        if (StrUtil.isBlank(userInput)) {
            return false;
        }
        
        String lowerInput = userInput.toLowerCase();
        for (String keyword : EMERGENCY_KEYWORDS) {
            if (lowerInput.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String buildSystemPrompt() {
        String masterPrompt = promptService.getMasterAgentPrompt();
        if (masterPrompt == null) {
            return getDefaultMasterPrompt();
        }
        return masterPrompt;
    }

    private String buildUserMessage(ConversationContext context, String userInput) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户当前输入: ").append(userInput).append("\n\n");
        
        if (context.getMessages() != null && !context.getMessages().isEmpty()) {
            sb.append("对话历史（最近5轮）：\n");
            int start = Math.max(0, context.getMessages().size() - 5);
            for (int i = start; i < context.getMessages().size(); i++) {
                Message msg = context.getMessages().get(i);
                sb.append("- ").append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
        }
        
        if (context.getCurrentPhase() != null) {
            sb.append("\n当前对话阶段: ").append(context.getCurrentPhase().getDescription());
        }
        
        if (context.getCurrentIntent() != null) {
            sb.append("\n当前识别意图: ").append(context.getCurrentIntent().getDescription());
        }
        
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private AgentAnalysisResult parseAnalysisResult(String content) {
        try {
            String jsonStr = extractJson(content);
            if (jsonStr == null) {
                log.warn("无法从响应中提取JSON");
                return null;
            }
            
            Map<String, Object> map = objectMapper.readValue(jsonStr, Map.class);
            
            AgentAnalysisResult result = AgentAnalysisResult.builder()
                    .intent((String) map.get("intent"))
                    .confidence(map.get("confidence") != null ? ((Number) map.get("confidence")).doubleValue() : 0.5)
                    .targetAgent((String) map.get("target_agent"))
                    .targetSkill((String) map.get("target_skill"))
                    .reasoning((String) map.get("reasoning"))
                    .nextAction((String) map.get("next_action"))
                    .immediateResponse((String) map.get("immediate_response"))
                    .build();
            
            return result;
        } catch (Exception e) {
            log.warn("解析主Agent结果失败: {}", e.getMessage());
            return null;
        }
    }

    private AgentAnalysisResult handleUnclearIntent(String userInput) {
        String lowerInput = userInput.toLowerCase();
        
        if (isGreeting(lowerInput)) {
            return AgentAnalysisResult.builder()
                    .intent(IntentType.GREETING.getCode())
                    .confidence(0.9)
                    .reasoning("用户发送问候语")
                    .nextAction("DIRECT_RESPONSE")
                    .immediateResponse("您好！我是您的医疗助手，很高兴为您服务。请问您有什么健康问题需要咨询吗？")
                    .build();
        }
        
        if (isConditionConsult(lowerInput)) {
            return AgentAnalysisResult.builder()
                    .intent(IntentType.CONDITION_CONSULT.getCode())
                    .confidence(0.7)
                    .targetAgent(AgentType.CONDITION_COLLECTION_AGENT.getCode())
                    .reasoning("用户描述了身体不适症状，可能需要病情咨询")
                    .nextAction("ROUTE_TO_AGENT")
                    .build();
        }
        
        if (isCaseQuery(lowerInput)) {
            return AgentAnalysisResult.builder()
                    .intent(IntentType.CASE_QUERY.getCode())
                    .confidence(0.7)
                    .targetAgent(AgentType.CASE_RETRIEVAL_AGENT.getCode())
                    .reasoning("用户询问疾病或治疗相关信息")
                    .nextAction("ROUTE_TO_AGENT")
                    .build();
        }
        
        return AgentAnalysisResult.builder()
                .intent(IntentType.UNCLEAR.getCode())
                .confidence(0.5)
                .reasoning("无法明确用户意图")
                .nextAction("ASK_CLARIFICATION")
                .immediateResponse("抱歉，我不太明白您的问题。请问您是有身体不适的症状需要咨询，还是想了解某种疾病的相关信息呢？")
                .build();
    }

    private boolean isGreeting(String input) {
        List<String> greetings = List.of("你好", "您好", "hi", "hello", "嗨", "在吗", "有人吗");
        for (String greeting : greetings) {
            if (input.contains(greeting)) {
                return true;
            }
        }
        return false;
    }

    private boolean isConditionConsult(String input) {
        List<String> keywords = List.of(
                "痛", "疼", "不舒服", "难受", "头晕", "头痛", "恶心", "呕吐",
                "咳嗽", "发烧", "感冒", "拉肚子", "便秘", "腹胀", "胸闷", "心慌",
                "乏力", "疲劳", "睡眠", "失眠", "过敏", "皮肤", "痒"
        );
        for (String keyword : keywords) {
            if (input.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCaseQuery(String input) {
        List<String> keywords = List.of(
                "什么是", "怎么治", "如何治", "治疗方法", "用药", "吃什么药",
                "症状", "表现", "诊断", "检查", "饮食", "注意事项", "预防",
                "糖尿病", "高血压", "心脏病", "胃炎", "胃溃疡", "肺炎", "肝炎"
        );
        for (String keyword : keywords) {
            if (input.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String getDefaultMasterPrompt() {
        return """
                你是一个专业的医疗助手系统的总控Agent。你的职责是分析用户的输入，识别用户的意图。
                
                意图类型：
                1. CONDITION_CONSULT - 病情咨询：用户描述身体不适、症状
                2. CASE_QUERY - 病例查询：用户询问疾病相关信息、治疗方案
                3. INFORMATION_CONFIRM - 信息确认：用户回答之前的问题
                4. GREETING - 普通问候：用户打招呼
                5. UNCLEAR - 模糊意图：无法明确判断
                
                请以JSON格式输出：
                {
                  "intent": "意图类型",
                  "confidence": 0.85,
                  "target_agent": "目标子Agent",
                  "target_skill": "目标Skill",
                  "reasoning": "推理过程",
                  "next_action": "下一步动作"
                }
                """;
    }

    private String extractJson(String content) {
        if (content == null) {
            return null;
        }
        
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        
        return null;
    }
}
