package com.feldsher.ai.service.agent;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feldsher.ai.dto.AgentAnalysisResult;
import com.feldsher.ai.entity.CollectedInfo;
import com.feldsher.ai.entity.ConversationContext;
import com.feldsher.ai.entity.Message;
import com.feldsher.ai.enums.ConversationPhase;
import com.feldsher.ai.service.PromptService;
import com.feldsher.ai.service.ToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseRetrievalAgentService {

    private final ChatClient chatClient;
    private final PromptService promptService;
    private final ToolService toolService;
    private final ObjectMapper objectMapper;

    public AgentAnalysisResult process(ConversationContext context, String userInput, List<String> searchTerms) {
        log.info("病例检索Agent处理，搜索关键词: {}", searchTerms);
        
        Map<String, Object> diseaseInfo = null;
        Map<String, Object> medicationInfo = null;
        Map<String, Object> lifestyleInfo = null;
        
        if (searchTerms != null && !searchTerms.isEmpty()) {
            for (String term : searchTerms) {
                if (diseaseInfo == null) {
                    diseaseInfo = toolService.webSearch(term, "DISEASE_INFO");
                }
                if (medicationInfo == null) {
                    medicationInfo = toolService.webSearch(term + " 用药", "MEDICATION");
                }
                if (lifestyleInfo == null) {
                    lifestyleInfo = toolService.webSearch(term + " 饮食注意", "LIFESTYLE");
                }
            }
        }
        
        String systemPrompt = buildCaseRetrievalPrompt(context);
        
        try {
            ChatResponse response = chatClient.prompt()
                    .messages(
                            new SystemMessage(systemPrompt),
                            new UserMessage(buildUserMessage(context, userInput, diseaseInfo, medicationInfo, lifestyleInfo))
                    )
                    .call()
                    .chatResponse();
            
            String content = response.getResult().getOutput().getText();
            log.info("病例检索结果: {}", content);
            
            return AgentAnalysisResult.builder()
                    .intent("CASE_QUERY")
                    .confidence(0.9)
                    .phase("DIAGNOSIS")
                    .summary(content)
                    .nextAction("TERMINATE")
                    .build();
        } catch (Exception e) {
            log.error("病例检索失败: {}", e.getMessage(), e);
            return generateDefaultResponse(context);
        }
    }

    public AgentAnalysisResult processDirectQuery(ConversationContext context, String userInput) {
        log.info("病例检索Agent直接处理查询: {}", userInput);
        
        return process(context, userInput, List.of(userInput));
    }

    private String buildCaseRetrievalPrompt(ConversationContext context) {
        String agentPrompt = promptService.getCaseRetrievalAgentPrompt();
        if (agentPrompt == null) {
            agentPrompt = getDefaultCaseRetrievalPrompt();
        }
        
        return agentPrompt;
    }

    private String buildUserMessage(ConversationContext context, String userInput,
                                      Map<String, Object> diseaseInfo,
                                      Map<String, Object> medicationInfo,
                                      Map<String, Object> lifestyleInfo) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("【用户查询】\n").append(userInput).append("\n\n");
        
        if (context.getCollectedInfo() != null) {
            sb.append("【收集的病情信息】\n");
            sb.append(formatCollectedInfo(context.getCollectedInfo())).append("\n\n");
        }
        
        sb.append("【检索到的信息】\n");
        
        if (diseaseInfo != null && !diseaseInfo.isEmpty()) {
            sb.append("疾病信息：").append(diseaseInfo.getOrDefault("summary", "暂无")).append("\n");
            Object keyPoints = diseaseInfo.get("key_points");
            if (keyPoints instanceof List) {
                List<?> points = (List<?>) keyPoints;
                for (Object point : points) {
                    sb.append("- ").append(point).append("\n");
                }
            }
        }
        
        if (medicationInfo != null && !medicationInfo.isEmpty()) {
            sb.append("\n用药信息：").append(medicationInfo.getOrDefault("summary", "暂无")).append("\n");
        }
        
        if (lifestyleInfo != null && !lifestyleInfo.isEmpty()) {
            sb.append("\n生活方式建议：").append(lifestyleInfo.getOrDefault("summary", "暂无")).append("\n");
        }
        
        sb.append("\n请根据以上信息，为用户提供专业的医疗建议。要求：\n");
        sb.append("1. 首先给出病情分析和可能的诊断\n");
        sb.append("2. 然后给出相似病例参考\n");
        sb.append("3. 接着给出用药建议（强调需在医生指导下使用）\n");
        sb.append("4. 最后给出生活方式和预防建议\n");
        sb.append("5. 结尾必须包含免责声明：以上内容仅供参考，不能替代专业医生的诊断和治疗\n");
        
        return sb.toString();
    }

    private AgentAnalysisResult generateDefaultResponse(ConversationContext context) {
        CollectedInfo info = context.getCollectedInfo();
        
        StringBuilder response = new StringBuilder();
        
        response.append("## 病情分析\n\n");
        
        if (info != null && info.getChiefComplaint() != null) {
            response.append("根据您描述的症状\"").append(info.getChiefComplaint()).append("\"，初步分析如下：\n\n");
            response.append("### 可能的情况\n");
            response.append("- 消化系统功能紊乱\n");
            response.append("- 可能存在炎症或溃疡\n");
            response.append("- 需要进一步检查确认\n\n");
        }
        
        response.append("### 建议检查项目\n");
        response.append("- 胃镜检查（如症状持续）\n");
        response.append("- 幽门螺杆菌检测\n");
        response.append("- 血常规、肝肾功能\n\n");
        
        response.append("## 用药建议\n\n");
        response.append("以下药物建议仅供参考，请在医生指导下使用：\n\n");
        response.append("1. **质子泵抑制剂**（如奥美拉唑）\n");
        response.append("   - 作用：抑制胃酸分泌\n");
        response.append("   - 用法：20mg，每日1-2次\n");
        response.append("   - 注意：长期使用需遵医嘱\n\n");
        
        response.append("2. **胃黏膜保护剂**（如铝碳酸镁）\n");
        response.append("   - 作用：保护胃黏膜\n");
        response.append("   - 用法：1-2片，每日3次，餐后1小时服用\n\n");
        
        response.append("## 生活方式建议\n\n");
        response.append("### 饮食调理\n");
        response.append("- 规律饮食，定时定量\n");
        response.append("- 避免辛辣、油腻、刺激性食物\n");
        response.append("- 避免咖啡、浓茶、酒精\n");
        response.append("- 细嚼慢咽，避免暴饮暴食\n");
        response.append("- 睡前2小时避免进食\n\n");
        
        response.append("### 生活习惯\n");
        response.append("- 戒烟限酒\n");
        response.append("- 保持良好心态，避免焦虑紧张\n");
        response.append("- 规律作息，避免熬夜\n");
        response.append("- 适当运动，增强体质\n\n");
        
        response.append("## 温馨提示\n\n");
        response.append("⚠️ **重要提示**：\n");
        response.append("- 如出现呕血、黑便、体重下降等报警症状，请立即就医\n");
        response.append("- 如症状持续不缓解或加重，请及时就医进行详细检查\n");
        response.append("- 建议定期进行健康体检\n\n");
        
        response.append("---\n");
        response.append("**免责声明**：以上内容仅供参考，不能替代专业医生的诊断和治疗。如有不适，请及时就医。");
        
        return AgentAnalysisResult.builder()
                .intent("CASE_QUERY")
                .confidence(0.8)
                .phase("DIAGNOSIS")
                .summary(response.toString())
                .nextAction("TERMINATE")
                .build();
    }

    private String formatCollectedInfo(CollectedInfo info) {
        if (info == null) {
            return "暂无";
        }
        
        List<String> parts = new ArrayList<>();
        
        if (info.getChiefComplaint() != null) parts.add("- 主诉：" + info.getChiefComplaint());
        if (info.getDuration() != null) parts.add("- 持续时间：" + info.getDuration());
        if (info.getLocation() != null) parts.add("- 部位：" + info.getLocation());
        if (info.getPainQuality() != null) parts.add("- 性质：" + info.getPainQuality());
        
        if (parts.isEmpty()) {
            return "暂无";
        }
        
        return String.join("\n", parts);
    }

    private String getDefaultCaseRetrievalPrompt() {
        return """
                你是一个专业的医疗病例检索Agent。你的职责是根据用户的病情信息，提供专业的医疗建议。
                
                回复结构要求：
                1. 病情分析和可能的诊断
                2. 相似病例参考
                3. 用药建议（强调需在医生指导下使用）
                4. 生活方式和预防建议
                
                重要原则：
                1. 所有建议仅供参考
                2. 不能替代专业医生的诊断
                3. 对于报警症状，强烈建议就医
                4. 结尾必须包含免责声明
                """;
    }
}
