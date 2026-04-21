package com.feldsher.ai.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feldsher.ai.entity.CollectedInfo;
import com.feldsher.ai.entity.ConversationContext;
import com.feldsher.ai.entity.Message;
import com.feldsher.ai.enums.SkillType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillService {

    private final ChatClient chatClient;
    private final PromptService promptService;
    private final ToolService toolService;
    private final ObjectMapper objectMapper;

    private static final int MIN_QUESTIONS = 3;
    private static final int MAX_QUESTIONS = 6;

    private static final Map<String, List<String>> DEPARTMENT_KEYWORDS = new HashMap<>();

    static {
        DEPARTMENT_KEYWORDS.put("internal_medicine_digestive", 
                Arrays.asList("胃", "胃痛", "腹痛", "腹胀", "恶心", "呕吐", "腹泻", "便秘", "反酸", "嗳气", "消化", "肠胃", "肚子", "胆囊", "肝", "胰腺"));
        DEPARTMENT_KEYWORDS.put("internal_medicine_respiratory",
                Arrays.asList("咳嗽", "咳痰", "胸闷", "气喘", "呼吸", "发热", "感冒", "肺", "气管", "鼻子", "喉咙", "痰"));
        DEPARTMENT_KEYWORDS.put("internal_medicine_cardiovascular",
                Arrays.asList("心脏", "胸痛", "胸闷", "心悸", "心慌", "高血压", "血压", "气短", "水肿", "心绞痛", "冠脉"));
        DEPARTMENT_KEYWORDS.put("internal_medicine_neurology",
                Arrays.asList("头痛", "头晕", "眩晕", "麻木", "无力", "抽搐", "中风", "脑梗", "记忆", "睡眠"));
        DEPARTMENT_KEYWORDS.put("internal_medicine_endocrine",
                Arrays.asList("糖尿病", "血糖", "甲状腺", "甲亢", "甲减", "体重", "怕热", "怕冷", "激素"));
        DEPARTMENT_KEYWORDS.put("orthopedics",
                Arrays.asList("骨折", "关节", "腰痛", "颈痛", "颈椎", "腰椎", "骨科", "扭伤", "拉伤", "骨头"));
        DEPARTMENT_KEYWORDS.put("dentistry",
                Arrays.asList("牙痛", "牙齿", "牙龈", "口腔", "智齿", "蛀牙", "拔牙"));
        DEPARTMENT_KEYWORDS.put("ophthalmology",
                Arrays.asList("眼睛", "视力", "眼痛", "眼红", "流泪", "近视", "眼科"));
        DEPARTMENT_KEYWORDS.put("otolaryngology",
                Arrays.asList("耳朵", "鼻子", "喉咙", "咽痛", "鼻塞", "流涕", "听力", "耳鼻喉"));
        DEPARTMENT_KEYWORDS.put("dermatology",
                Arrays.asList("皮肤", "皮疹", "瘙痒", "过敏", "湿疹", "皮炎", "痘痘"));
    }

    public SkillType selectSkill(String userInput, String context) {
        log.info("开始选择Skill，用户输入: {}", userInput);
        
        Map<String, Object> result = toolService.skillSelector(userInput, context);
        
        String selectedSkill = (String) result.get("selected_skill");
        if (selectedSkill == null) {
            selectedSkill = selectSkillByKeywords(userInput);
        }
        
        SkillType skillType = SkillType.fromCode(selectedSkill);
        if (skillType == null) {
            skillType = SkillType.INTERNAL_MEDICINE_DIGESTIVE;
        }
        
        log.info("选择的Skill: {}", skillType.getName());
        return skillType;
    }

    private String selectSkillByKeywords(String userInput) {
        String input = userInput.toLowerCase();
        
        for (Map.Entry<String, List<String>> entry : DEPARTMENT_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (input.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        
        return "internal_medicine_digestive";
    }

    public List<String> generateQuestions(ConversationContext context) {
        log.info("生成问诊问题，当前Skill: {}", context.getCurrentSkill());
        
        String skillPrompt = promptService.getSkillPrompt(context.getCurrentSkill());
        if (skillPrompt == null) {
            skillPrompt = getDefaultSkillPrompt();
        }
        
        String systemPrompt = buildQuestionGenerationPrompt(skillPrompt, context);
        
        try {
            ChatResponse response = chatClient.prompt()
                    .messages(
                            new SystemMessage(systemPrompt),
                            new UserMessage(buildUserMessageForQuestions(context))
                    )
                    .call()
                    .chatResponse();
            
            String content = response.getResult().getOutput().getText();
            List<String> questions = parseQuestionsFromResponse(content);
            
            if (questions.isEmpty()) {
                questions = getDefaultQuestions(context);
            }
            
            log.info("生成的问题: {}", questions);
            return questions;
        } catch (Exception e) {
            log.error("生成问题失败: {}", e.getMessage(), e);
            return getDefaultQuestions(context);
        }
    }

    private String buildQuestionGenerationPrompt(String skillPrompt, ConversationContext context) {
        return """
                你是一名专业的医生，正在为患者进行问诊。根据以下信息，生成3-6个与患者症状相关的问题。
                
                问诊原则：
                1. 先询问与当前症状最相关的问题
                2. 问题要具体、清晰
                3. 避免重复询问已知道的信息
                4. 问题类型可以包括：症状特点、发作时间、诱发因素、缓解因素、伴随症状等
                
                科室问诊模板：
                %s
                
                当前对话上下文：
                - 主要症状：%s
                - 已收集信息：%s
                - 已问问题数量：%d
                
                请以JSON格式输出问题列表：
                {
                  "questions": [
                    "问题1",
                    "问题2",
                    "问题3"
                  ]
                }
                """.formatted(
                        skillPrompt,
                        getChiefComplaint(context),
                        getCollectedInfoSummary(context),
                        context.getQuestionCount()
                );
    }

    private String buildUserMessageForQuestions(ConversationContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以上信息，为我生成问诊问题。\n\n");
        
        if (context.getMessages() != null && !context.getMessages().isEmpty()) {
            sb.append("对话历史：\n");
            int start = Math.max(0, context.getMessages().size() - 5);
            for (int i = start; i < context.getMessages().size(); i++) {
                Message msg = context.getMessages().get(i);
                sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
        }
        
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> parseQuestionsFromResponse(String content) {
        try {
            String jsonStr = extractJson(content);
            if (jsonStr == null) {
                return new ArrayList<>();
            }
            
            Map<String, Object> map = objectMapper.readValue(jsonStr, Map.class);
            Object questionsObj = map.get("questions");
            
            if (questionsObj instanceof List) {
                List<?> list = (List<?>) questionsObj;
                List<String> result = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof String) {
                        result.add((String) item);
                    }
                }
                return result;
            }
            
            return new ArrayList<>();
        } catch (Exception e) {
            log.warn("解析问题列表失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<String> getDefaultQuestions(ConversationContext context) {
        List<String> questions = new ArrayList<>();
        questions.add("这个症状是从什么时候开始的？");
        questions.add("每天大概发作几次？每次持续多久？");
        questions.add("这种不舒服是什么样的感觉？比如刺痛、胀痛、还是隐隐作痛？");
        questions.add("疼痛的具体位置在哪里？会放射到其他地方吗？");
        questions.add("有没有什么情况会让这个症状加重？比如运动后、饭后？");
        questions.add("除了这个症状，还有没有其他不舒服的地方？比如恶心、呕吐、发烧？");
        
        return questions;
    }

    private String getChiefComplaint(ConversationContext context) {
        if (context.getCollectedInfo() != null && context.getCollectedInfo().getChiefComplaint() != null) {
            return context.getCollectedInfo().getChiefComplaint();
        }
        if (context.getMessages() != null && !context.getMessages().isEmpty()) {
            for (Message msg : context.getMessages()) {
                if (msg.getRole() == Message.Role.USER) {
                    return msg.getContent();
                }
            }
        }
        return "未知";
    }

    private String getCollectedInfoSummary(ConversationContext context) {
        if (context.getCollectedInfo() == null) {
            return "暂无";
        }
        
        CollectedInfo info = context.getCollectedInfo();
        List<String> parts = new ArrayList<>();
        
        if (info.getChiefComplaint() != null) parts.add("主诉: " + info.getChiefComplaint());
        if (info.getDuration() != null) parts.add("持续时间: " + info.getDuration());
        if (info.getLocation() != null) parts.add("部位: " + info.getLocation());
        if (info.getPainQuality() != null) parts.add("性质: " + info.getPainQuality());
        
        return parts.isEmpty() ? "暂无" : String.join("; ", parts);
    }

    private String getDefaultSkillPrompt() {
        return """
                通用问诊模板：
                
                1. 症状特点
                   - 什么时候开始的？
                   - 持续多久了？
                   - 是什么样的感觉？
                   
                2. 发作规律
                   - 每天发作几次？
                   - 什么时候比较严重？
                   
                3. 诱发与缓解
                   - 什么情况下加重？
                   - 什么情况下缓解？
                   
                4. 伴随症状
                   - 还有其他不舒服吗？
                   - 有没有发烧、恶心、呕吐？
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

    public CollectedInfo updateCollectedInfo(CollectedInfo info, String userAnswer, String question) {
        if (info == null) {
            info = CollectedInfo.builder().build();
        }
        
        String answerLower = userAnswer.toLowerCase();
        
        if (question.contains("什么时候开始") || question.contains("多久")) {
            if (info.getDuration() == null) {
                info.setDuration(userAnswer);
            }
            if (info.getOnset() == null) {
                info.setOnset(userAnswer);
            }
        }
        
        if (question.contains("什么样的感觉") || question.contains("疼痛")) {
            if (info.getPainQuality() == null) {
                info.setPainQuality(userAnswer);
            }
        }
        
        if (question.contains("位置") || question.contains("哪里")) {
            if (info.getLocation() == null) {
                info.setLocation(userAnswer);
            }
        }
        
        if (question.contains("加重") || question.contains("缓解")) {
            if (answerLower.contains("加重") || answerLower.contains("更严重")) {
                info.addAggravatingFactor(userAnswer);
            } else if (answerLower.contains("缓解") || answerLower.contains("好一点")) {
                info.addRelievingFactor(userAnswer);
            }
        }
        
        if (question.contains("其他不舒服") || question.contains("伴随")) {
            if (!answerLower.contains("没有") && !answerLower.contains("无")) {
                info.addAssociatedSymptom(userAnswer);
            }
        }
        
        return info;
    }
}
