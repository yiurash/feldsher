package com.feldsher.ai.enums;

import lombok.Getter;

@Getter
public enum ConversationPhase {

    INITIAL("initial", "初始阶段"),
    INTENT_ANALYSIS("intent_analysis", "意图分析阶段"),
    SKILL_SELECTION("skill_selection", "技能选择阶段"),
    QUESTIONING("questioning", "问诊阶段"),
    INFORMATION_CONFIRMATION("information_confirmation", "信息确认阶段"),
    DIAGNOSIS("diagnosis", "诊断阶段"),
    CASE_RETRIEVAL("case_retrieval", "病例检索阶段"),
    SUMMARIZING("summarizing", "总结阶段"),
    EMERGENCY("emergency", "紧急情况"),
    TERMINATED("terminated", "对话结束");

    private final String code;
    private final String description;

    ConversationPhase(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
