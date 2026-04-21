package com.feldsher.ai.enums;

import lombok.Getter;

@Getter
public enum AgentType {

    MASTER_AGENT("master_agent", "主Agent"),
    CONDITION_COLLECTION_AGENT("condition_collection_agent", "病情采集Agent"),
    CASE_RETRIEVAL_AGENT("case_retrieval_agent", "病例检索Agent");

    private final String code;
    private final String name;

    AgentType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static AgentType fromCode(String code) {
        for (AgentType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
