package com.feldsher.ai.service;

import com.feldsher.ai.enums.AgentType;
import com.feldsher.ai.enums.SkillType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class PromptService {

    private final Map<String, String> promptCache = new ConcurrentHashMap<>();

    private static final String AGENT_PROMPT_PATH = "prompts/agents/";
    private static final String SKILL_PROMPT_PATH = "prompts/skills/";

    @PostConstruct
    public void init() {
        log.info("开始加载提示词文档...");
        
        loadAgentPrompts();
        loadSkillPrompts();
        
        log.info("提示词文档加载完成，共加载 {} 个提示词", promptCache.size());
    }

    private void loadAgentPrompts() {
        for (AgentType agentType : AgentType.values()) {
            String fileName = agentType.getCode() + ".md";
            String content = loadPrompt(AGENT_PROMPT_PATH + fileName);
            if (content != null) {
                promptCache.put("agent:" + agentType.getCode(), content);
                log.info("加载Agent提示词: {}", agentType.getName());
            }
        }
    }

    private void loadSkillPrompts() {
        for (SkillType skillType : SkillType.values()) {
            String subPath = skillType.isParent() ? "parent/" : "children/";
            String fileName = skillType.getCode() + ".md";
            String content = loadPrompt(SKILL_PROMPT_PATH + subPath + fileName);
            if (content != null) {
                promptCache.put("skill:" + skillType.getCode(), content);
                log.info("加载Skill提示词: {}", skillType.getName());
            }
        }
    }

    private String loadPrompt(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                log.debug("提示词文件不存在: {}", path);
                return null;
            }
            
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            
            return content.toString();
        } catch (Exception e) {
            log.warn("加载提示词失败: {}, 错误: {}", path, e.getMessage());
            return null;
        }
    }

    public String getAgentPrompt(AgentType agentType) {
        return promptCache.get("agent:" + agentType.getCode());
    }

    public String getSkillPrompt(SkillType skillType) {
        return promptCache.get("skill:" + skillType.getCode());
    }

    public String getMasterAgentPrompt() {
        return getAgentPrompt(AgentType.MASTER_AGENT);
    }

    public String getConditionCollectionAgentPrompt() {
        return getAgentPrompt(AgentType.CONDITION_COLLECTION_AGENT);
    }

    public String getCaseRetrievalAgentPrompt() {
        return getAgentPrompt(AgentType.CASE_RETRIEVAL_AGENT);
    }

    public String getParentConditionCollectionSkillPrompt() {
        return getSkillPrompt(SkillType.PARENT_CONDITION_COLLECTION);
    }

    public String getDigestiveSkillPrompt() {
        return getSkillPrompt(SkillType.INTERNAL_MEDICINE_DIGESTIVE);
    }

    public String getRespiratorySkillPrompt() {
        return getSkillPrompt(SkillType.INTERNAL_MEDICINE_RESPIRATORY);
    }

    public String getCardiovascularSkillPrompt() {
        return getSkillPrompt(SkillType.INTERNAL_MEDICINE_CARDIOVASCULAR);
    }
}
