package com.feldsher.ai.enums;

import lombok.Getter;

@Getter
public enum IntentType {

    CONDITION_CONSULT("CONDITION_CONSULT", "病情咨询"),
    CASE_QUERY("CASE_QUERY", "病例查询"),
    INFORMATION_CONFIRM("INFORMATION_CONFIRM", "信息确认"),
    GREETING("GREETING", "普通问候"),
    UNCLEAR("UNCLEAR", "模糊意图");

    private final String code;
    private final String description;

    IntentType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static IntentType fromCode(String code) {
        for (IntentType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return UNCLEAR;
    }
}
