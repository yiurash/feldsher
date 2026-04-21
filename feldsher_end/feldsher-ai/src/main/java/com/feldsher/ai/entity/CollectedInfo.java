package com.feldsher.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectedInfo {

    private String chiefComplaint;
    private String duration;
    private String onset;
    private String frequency;
    private String painQuality;
    private String painSeverity;
    private String location;
    private String radiation;
    private List<String> aggravatingFactors;
    private List<String> relievingFactors;
    private List<String> associatedSymptoms;
    private MedicalHistory medicalHistory;
    private PersonalHistory personalHistory;
    private FamilyHistory familyHistory;
    private Map<String, String> additionalInfo;
    private List<String> redFlags;

    public void addAggravatingFactor(String factor) {
        if (aggravatingFactors == null) {
            aggravatingFactors = new ArrayList<>();
        }
        aggravatingFactors.add(factor);
    }

    public void addRelievingFactor(String factor) {
        if (relievingFactors == null) {
            relievingFactors = new ArrayList<>();
        }
        relievingFactors.add(factor);
    }

    public void addAssociatedSymptom(String symptom) {
        if (associatedSymptoms == null) {
            associatedSymptoms = new ArrayList<>();
        }
        associatedSymptoms.add(symptom);
    }

    public void addRedFlag(String flag) {
        if (redFlags == null) {
            redFlags = new ArrayList<>();
        }
        redFlags.add(flag);
    }

    public void addAdditionalInfo(String key, String value) {
        if (additionalInfo == null) {
            additionalInfo = new HashMap<>();
        }
        additionalInfo.put(key, value);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MedicalHistory {
        private List<String> chronicConditions;
        private List<String> previousSurgeries;
        private List<String> allergies;
        private List<String> currentMedications;
        private String previousTests;
        private Boolean hasSimilarEpisodes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PersonalHistory {
        private Boolean smoking;
        private Integer cigarettesPerDay;
        private Integer smokingYears;
        private Boolean alcohol;
        private String alcoholType;
        private String alcoholAmount;
        private String diet;
        private String exercise;
        private String sleep;
        private String stress;
        private String recentExposures;
        private String recentTravel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FamilyHistory {
        private List<String> familyDiseases;
        private List<String> cancerHistory;
        private List<String> geneticDisorders;
    }
}
