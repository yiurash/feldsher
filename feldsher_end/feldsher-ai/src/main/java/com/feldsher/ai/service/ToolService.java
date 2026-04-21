package com.feldsher.ai.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final PromptService promptService;

    private static final String SKILL_SELECTOR_SYSTEM_PROMPT = """
            你是一个医疗科室选择器。根据用户的症状描述，判断应该使用哪个具体的病科技能。
            
            可用的病科技能：
            1. internal_medicine_digestive - 消化内科：处理胃痛、胃胀、恶心、呕吐、腹泻、便秘、反酸、嗳气、黄疸、食欲不振等消化系统症状
            2. internal_medicine_respiratory - 呼吸内科：处理咳嗽、咳痰、咯血、胸痛、呼吸困难、胸闷、气喘、发热等呼吸系统症状
            3. internal_medicine_cardiovascular - 心血管内科：处理胸痛、胸闷、心悸、气短、水肿、头晕、晕厥、高血压等心血管症状
            4. internal_medicine_neurology - 神经内科：处理头痛、头晕、眩晕、肢体麻木、无力、抽搐、意识障碍、记忆减退等神经系统症状
            5. internal_medicine_endocrine - 内分泌科：处理多饮、多尿、多食、体重变化、怕热/怕冷、情绪异常等内分泌症状
            6. general_surgery - 普外科：处理腹痛、腹部包块、疝气、外伤等外科症状
            7. orthopedics - 骨科：处理疼痛、肿胀、畸形、活动受限、外伤、腰痛、颈痛等骨科症状
            8. dentistry - 口腔科：处理牙痛、牙龈出血、口腔溃疡、张口受限等口腔症状
            9. ophthalmology - 眼科：处理视力下降、眼痛、眼红、流泪、畏光、视物模糊等眼科症状
            10. otolaryngology - 耳鼻喉科：处理耳痛、听力下降、鼻塞、流涕、咽痛、声音嘶哑等耳鼻喉症状
            11. dermatology - 皮肤科：处理皮疹、瘙痒、红斑、水疱、脱屑、色素沉着等皮肤科症状
            
            请以JSON格式输出：
            {
              "selected_skill": "skill_code",
              "skill_name": "技能名称",
              "confidence": 0.85,
              "reasoning": "判断理由",
              "key_symptoms": ["症状1", "症状2"]
            }
            """;

    private static final String WEB_SEARCH_SYSTEM_PROMPT = """
            你是一个专业的医学知识检索助手。请根据用户的查询，提供专业的医学信息。
            
            搜索类型说明：
            - DISEASE_INFO: 疾病信息，包括症状、诊断、治疗等
            - MEDICATION: 用药信息，包括用法、剂量、副作用等
            - CASE_STUDY: 病例研究，包括典型病例、治疗方案等
            - LIFESTYLE: 生活方式建议，包括饮食、运动、作息等
            
            请以JSON格式输出：
            {
              "query_type": "查询类型",
              "summary": "信息摘要",
              "key_points": ["要点1", "要点2", "要点3"],
              "references": ["参考来源1", "参考来源2"],
              "disclaimer": "以上信息仅供参考，不能替代专业医生的诊断和治疗"
            }
            """;

    public Map<String, Object> skillSelector(String userInput, String context) {
        log.info("调用skill_selector工具，用户输入: {}", userInput);
        
        try {
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(SKILL_SELECTOR_SYSTEM_PROMPT));
            
            String userMessage = "用户输入: " + userInput;
            if (StrUtil.isNotBlank(context)) {
                userMessage += "\n对话上下文: " + context;
            }
            messages.add(new UserMessage(userMessage));
            
            ChatResponse response = chatClient.prompt()
                    .messages(messages)
                    .call()
                    .chatResponse();
            
            String content = response.getResult().getOutput().getText();
            log.info("skill_selector结果: {}", content);
            
            return parseJsonResponse(content);
        } catch (Exception e) {
            log.error("skill_selector调用失败: {}", e.getMessage(), e);
            Map<String, Object> defaultResult = new HashMap<>();
            defaultResult.put("selected_skill", "internal_medicine_digestive");
            defaultResult.put("skill_name", "消化内科");
            defaultResult.put("confidence", 0.5);
            defaultResult.put("reasoning", "默认选择，无法自动判断");
            defaultResult.put("key_symptoms", List.of());
            return defaultResult;
        }
    }

    public String askUserQuestion(String question, String questionType) {
        log.info("调用ask_user_question工具，问题: {}, 类型: {}", question, questionType);
        return question;
    }

    public Map<String, Object> webSearch(String query, String searchType) {
        log.info("调用web_search工具，查询: {}, 类型: {}", query, searchType);
        
        try {
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(WEB_SEARCH_SYSTEM_PROMPT));
            messages.add(new UserMessage("查询内容: " + query + "\n查询类型: " + searchType));
            
            ChatResponse response = chatClient.prompt()
                    .messages(messages)
                    .call()
                    .chatResponse();
            
            String content = response.getResult().getOutput().getText();
            log.info("web_search结果: {}", content);
            
            return parseJsonResponse(content);
        } catch (Exception e) {
            log.error("web_search调用失败: {}", e.getMessage(), e);
            Map<String, Object> defaultResult = new HashMap<>();
            defaultResult.put("query_type", searchType);
            defaultResult.put("summary", "无法获取相关信息");
            defaultResult.put("key_points", List.of());
            defaultResult.put("references", List.of());
            defaultResult.put("disclaimer", "以上信息仅供参考");
            return defaultResult;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonResponse(String content) {
        try {
            String jsonStr = extractJson(content);
            if (jsonStr == null) {
                log.warn("无法从响应中提取JSON: {}", content);
                return new HashMap<>();
            }
            return objectMapper.readValue(jsonStr, Map.class);
        } catch (Exception e) {
            log.warn("解析JSON响应失败: {}, 内容: {}", e.getMessage(), content);
            return new HashMap<>();
        }
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
