package com.feldsher.ai.enums;

import lombok.Getter;

@Getter
public enum SkillType {

    PARENT_CONDITION_COLLECTION("parent_condition_collection", "病情采集父Skill", true),
    PARENT_CASE_RETRIEVAL("parent_case_retrieval", "病例检索父Skill", true),
    
    INTERNAL_MEDICINE_DIGESTIVE("internal_medicine_digestive", "消化内科", false),
    INTERNAL_MEDICINE_RESPIRATORY("internal_medicine_respiratory", "呼吸内科", false),
    INTERNAL_MEDICINE_CARDIOVASCULAR("internal_medicine_cardiovascular", "心血管内科", false),
    INTERNAL_MEDICINE_NEUROLOGY("internal_medicine_neurology", "神经内科", false),
    INTERNAL_MEDICINE_ENDOCRINE("internal_medicine_endocrine", "内分泌科", false),
    GENERAL_SURGERY("general_surgery", "普外科", false),
    ORTHOPEDICS("orthopedics", "骨科", false),
    DENTISTRY("dentistry", "口腔科", false),
    OPHTHALMOLOGY("ophthalmology", "眼科", false),
    OTOLARYNGOLOGY("otolaryngology", "耳鼻喉科", false),
    DERMATOLOGY("dermatology", "皮肤科", false);

    private final String code;
    private final String name;
    private final boolean isParent;

    SkillType(String code, String name, boolean isParent) {
        this.code = code;
        this.name = name;
        this.isParent = isParent;
    }

    public static SkillType fromCode(String code) {
        for (SkillType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
