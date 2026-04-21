package com.feldsher.ai.service.agent;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feldsher.ai.dto.AgentAnalysisResult;
import com.feldsher.ai.entity.CollectedInfo;
import com.feldsher.ai.entity.ConversationContext;
import com.feldsher.ai.entity.Message;
import com.feldsher.ai.enums.ConversationPhase;
import com.feldsher.ai.service.PromptService;
import com.feldsher.ai.service.SkillService;
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
public class ConditionCollectionAgentService {

    private final ChatClient chatClient;
    private final PromptService promptService;
    private final SkillService skillService;
    private final ObjectMapper objectMapper;

    private static final int MIN_QUESTIONS = 3;
    private static final int MAX_QUESTIONS = 6;
    private static final int TOTAL_QUESTIONS_TARGET = 15;

    public AgentAnalysisResult process(ConversationContext context, String userInput) {
        log.info("病情采集Agent处理，当前阶段: {}, 已问问题: {}", 
                context.getCurrentPhase(), context.getQuestionCount());
        
        if (context.getCurrentPhase() == ConversationPhase.SKILL_SELECTION) {
            return selectSkill(context, userInput);
        }
        
        if (context.getCurrentPhase() == ConversationPhase.QUESTIONING) {
            return processAnswer(context, userInput);
        }
        
        if (context.getCurrentPhase() == ConversationPhase.SUMMARIZING) {
            return generateSummary(context);
        }
        
        return startQuestioning(context, userInput);
    }

    private AgentAnalysisResult selectSkill(ConversationContext context, String userInput) {
        log.info("病情采集Agent：选择Skill");
        
        var skillType = skillService.selectSkill(userInput, buildContextSummary(context));
        context.setCurrentSkill(skillType);
        
        log.info("选择的Skill: {}", skillType.getName());
        
        return startQuestioning(context, userInput);
    }

    private AgentAnalysisResult startQuestioning(ConversationContext context, String userInput) {
        log.info("病情采集Agent：开始问诊");
        
        if (context.getCollectedInfo() == null) {
            context.setCollectedInfo(CollectedInfo.builder().build());
        }
        
        if (context.getCollectedInfo().getChiefComplaint() == null) {
            context.getCollectedInfo().setChiefComplaint(userInput);
        }
        
        context.setCurrentPhase(ConversationPhase.QUESTIONING);
        
        List<String> questions = skillService.generateQuestions(context);
        
        if (questions.size() > MAX_QUESTIONS) {
            questions = questions.subList(0, MAX_QUESTIONS);
        }
        
        List<AgentAnalysisResult.Question> questionList = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            questionList.add(AgentAnalysisResult.Question.builder()
                    .id("q" + (context.getQuestionCount() + i + 1))
                    .text(questions.get(i))
                    .type("SYMPTOM_DETAIL")
                    .required(true)
                    .build());
        }
        
        StringBuilder intro = new StringBuilder();
        intro.append("为了更好地了解您的情况，请回答以下几个问题：\n\n");
        
        for (int i = 0; i < questionList.size(); i++) {
            intro.append(i + 1).append(". ").append(questionList.get(i).getText()).append("\n");
        }
        
        context.setPendingQuestions(questions);
        
        return AgentAnalysisResult.builder()
                .intent("CONDITION_CONSULT")
                .confidence(0.9)
                .phase("QUESTIONING")
                .questions(questionList)
                .progress(calculateProgress(context))
                .nextAction("WAIT_USER_ANSWER")
                .immediateResponse(intro.toString())
                .build();
    }

    private AgentAnalysisResult processAnswer(ConversationContext context, String userAnswer) {
        log.info("病情采集Agent：处理用户回答");
        
        if (context.getCollectedInfo() == null) {
            context.setCollectedInfo(CollectedInfo.builder().build());
        }
        
        List<String> pendingQuestions = context.getPendingQuestions();
        if (pendingQuestions != null && !pendingQuestions.isEmpty()) {
            String question = pendingQuestions.get(0);
            context.getCollectedInfo().setAdditionalInfo(
                    skillService.updateCollectedInfo(
                            context.getCollectedInfo(), 
                            userAnswer, 
                            question
                    ).getAdditionalInfo()
            );
            pendingQuestions.remove(0);
        }
        
        context.incrementQuestionCount();
        
        if (shouldContinueQuestioning(context)) {
            List<String> newQuestions = skillService.generateQuestions(context);
            if (newQuestions != null && !newQuestions.isEmpty()) {
                int remaining = Math.min(MAX_QUESTIONS, newQuestions.size());
                if (remaining > 0) {
                    List<String> nextQuestions = newQuestions.subList(0, Math.min(remaining, newQuestions.size()));
                    context.setPendingQuestions(nextQuestions);
                    
                    List<AgentAnalysisResult.Question> questionList = new ArrayList<>();
                    for (int i = 0; i < nextQuestions.size(); i++) {
                        questionList.add(AgentAnalysisResult.Question.builder()
                                .id("q" + (context.getQuestionCount() + i + 1))
                                .text(nextQuestions.get(i))
                                .type("SYMPTOM_DETAIL")
                                .required(true)
                                .build());
                    }
                    
                    StringBuilder intro = new StringBuilder();
                    intro.append("还有几个问题想了解一下：\n\n");
                    
                    for (int i = 0; i < questionList.size(); i++) {
                        intro.append(i + 1).append(". ").append(questionList.get(i).getText()).append("\n");
                    }
                    
                    return AgentAnalysisResult.builder()
                            .intent("CONDITION_CONSULT")
                            .confidence(0.9)
                            .phase("QUESTIONING")
                            .questions(questionList)
                            .progress(calculateProgress(context))
                            .nextAction("WAIT_USER_ANSWER")
                            .immediateResponse(intro.toString())
                            .build();
                }
            }
        }
        
        return finishQuestioning(context);
    }

    private boolean shouldContinueQuestioning(ConversationContext context) {
        if (context.getQuestionCount() >= TOTAL_QUESTIONS_TARGET) {
            return false;
        }
        
        List<String> pending = context.getPendingQuestions();
        if (pending != null && !pending.isEmpty()) {
            return true;
        }
        
        return context.getQuestionCount() < MIN_QUESTIONS;
    }

    private AgentAnalysisResult finishQuestioning(ConversationContext context) {
        log.info("病情采集Agent：完成问诊，准备诊断");
        
        context.setCurrentPhase(ConversationPhase.SUMMARIZING);
        
        return generateSummary(context);
    }

    private AgentAnalysisResult generateSummary(ConversationContext context) {
        log.info("病情采集Agent：生成病情摘要");
        
        String systemPrompt = buildSummaryPrompt(context);
        
        try {
            ChatResponse response = chatClient.prompt()
                    .messages(
                            new SystemMessage(systemPrompt),
                            new UserMessage(buildUserMessageForSummary(context))
                    )
                    .call()
                    .chatResponse();
            
            String content = response.getResult().getOutput().getText();
            log.info("病情摘要生成结果: {}", content);
            
            AgentAnalysisResult result = parseSummaryResult(content);
            
            if (result.getSummary() == null) {
                result = generateDefaultSummary(context);
            }
            
            return result;
        } catch (Exception e) {
            log.error("生成病情摘要失败: {}", e.getMessage(), e);
            return generateDefaultSummary(context);
        }
    }

    private String buildSummaryPrompt(ConversationContext context) {
        String agentPrompt = promptService.getConditionCollectionAgentPrompt();
        if (agentPrompt == null) {
            agentPrompt = getDefaultConditionCollectionPrompt();
        }
        
        return agentPrompt + """
                
                当前收集到的信息：
                %s
                
                请根据以上信息，生成病情分析报告。
                """.formatted(formatCollectedInfo(context));
    }

    private String buildUserMessageForSummary(ConversationContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下对话历史和收集到的信息，生成病情分析报告。\n\n");
        
        if (context.getMessages() != null && !context.getMessages().isEmpty()) {
            sb.append("对话历史：\n");
            for (Message msg : context.getMessages()) {
                sb.append("- ").append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
        }
        
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private AgentAnalysisResult parseSummaryResult(String content) {
        try {
            String jsonStr = extractJson(content);
            if (jsonStr == null) {
                return null;
            }
            
            Map<String, Object> map = objectMapper.readValue(jsonStr, Map.class);
            
            AgentAnalysisResult result = AgentAnalysisResult.builder()
                    .phase((String) map.get("phase"))
                    .summary((String) map.get("summary"))
                    .nextAction((String) map.get("next_action"))
                    .build();
            
            Object possibleDiagnoses = map.get("possible_diagnoses");
            if (possibleDiagnoses instanceof List) {
                result.setPossibleDiagnoses((List<String>) possibleDiagnoses);
            }
            
            Object recommendedSearchTerms = map.get("recommended_search_terms");
            if (recommendedSearchTerms instanceof List) {
                result.setRecommendedSearchTerms((List<String>) recommendedSearchTerms);
            }
            
            return result;
        } catch (Exception e) {
            log.warn("解析摘要结果失败: {}", e.getMessage());
            return null;
        }
    }

    private AgentAnalysisResult generateDefaultSummary(ConversationContext context) {
        CollectedInfo info = context.getCollectedInfo();
        
        StringBuilder summary = new StringBuilder();
        summary.append("根据您描述的症状，我已收集到以下信息：\n\n");
        
        if (info != null) {
            if (info.getChiefComplaint() != null) {
                summary.append("【主要症状】").append(info.getChiefComplaint()).append("\n");
            }
            if (info.getDuration() != null) {
                summary.append("【持续时间】").append(info.getDuration()).append("\n");
            }
            if (info.getLocation() != null) {
                summary.append("【不适部位】").append(info.getLocation()).append("\n");
            }
            if (info.getPainQuality() != null) {
                summary.append("【症状性质】").append(info.getPainQuality()).append("\n");
            }
        }
        
        summary.append("\n为了给您更准确的建议，我需要进一步查询相关的医学知识和病例信息。");
        
        List<String> defaultDiagnoses = new ArrayList<>();
        defaultDiagnoses.add("需要进一步分析确定");
        
        List<String> defaultSearchTerms = new ArrayList<>();
        if (info != null && info.getChiefComplaint() != null) {
            defaultSearchTerms.add(info.getChiefComplaint() + " 症状分析");
            defaultSearchTerms.add(info.getChiefComplaint() + " 可能原因");
        }
        defaultSearchTerms.add("消化系统常见疾病");
        
        return AgentAnalysisResult.builder()
                .phase("SUMMARIZING")
                .summary(summary.toString())
                .possibleDiagnoses(defaultDiagnoses)
                .recommendedSearchTerms(defaultSearchTerms)
                .nextAction("REQUEST_CASE_RETRIEVAL")
                .build();
    }

    private double calculateProgress(ConversationContext context) {
        int current = context.getQuestionCount();
        return Math.min(1.0, (double) current / TOTAL_QUESTIONS_TARGET);
    }

    private String formatCollectedInfo(ConversationContext context) {
        if (context.getCollectedInfo() == null) {
            return "暂无收集到的信息";
        }
        
        CollectedInfo info = context.getCollectedInfo();
        List<String> parts = new ArrayList<>();
        
        if (info.getChiefComplaint() != null) parts.add("- 主要症状：" + info.getChiefComplaint());
        if (info.getDuration() != null) parts.add("- 持续时间：" + info.getDuration());
        if (info.getOnset() != null) parts.add("- 起病方式：" + info.getOnset());
        if (info.getLocation() != null) parts.add("- 部位：" + info.getLocation());
        if (info.getPainQuality() != null) parts.add("- 性质：" + info.getPainQuality());
        if (info.getPainSeverity() != null) parts.add("- 程度：" + info.getPainSeverity());
        if (info.getAggravatingFactors() != null && !info.getAggravatingFactors().isEmpty()) {
            parts.add("- 加重因素：" + String.join(", ", info.getAggravatingFactors()));
        }
        if (info.getRelievingFactors() != null && !info.getRelievingFactors().isEmpty()) {
            parts.add("- 缓解因素：" + String.join(", ", info.getRelievingFactors()));
        }
        if (info.getAssociatedSymptoms() != null && !info.getAssociatedSymptoms().isEmpty()) {
            parts.add("- 伴随症状：" + String.join(", ", info.getAssociatedSymptoms()));
        }
        
        if (parts.isEmpty()) {
            return "暂无收集到的信息";
        }
        
        return String.join("\n", parts);
    }

    private String buildContextSummary(ConversationContext context) {
        if (context.getMessages() == null || context.getMessages().isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (Message msg : context.getMessages()) {
            sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("; ");
        }
        return sb.toString();
    }

    private String getDefaultConditionCollectionPrompt() {
        return """
                你是一个专业的医疗病情采集Agent。你的职责是通过与用户对话，系统地收集用户的病情信息。
                
                问诊原则：
                1. 先询问与当前症状最相关的问题
                2. 每次询问3-6个问题
                3. 问题要具体、清晰
                
                输出格式：
                当完成问诊后，请以JSON格式输出：
                {
                  "phase": "SUMMARIZING",
                  "summary": "病情摘要",
                  "possible_diagnoses": ["可能诊断1", "可能诊断2"],
                  "recommended_search_terms": ["搜索关键词1", "搜索关键词2"],
                  "next_action": "REQUEST_CASE_RETRIEVAL"
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
