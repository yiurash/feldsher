package com.feldsher.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentAnalysisResult {

    private String intent;
    private Double confidence;
    private String targetAgent;
    private String targetSkill;
    private String reasoning;
    private String nextAction;
    private String immediateResponse;
    private String phase;
    private List<Question> questions;
    private CollectedData collectedData;
    private Double progress;
    private String summary;
    private List<String> possibleDiagnoses;
    private List<String> recommendedSearchTerms;
    private Boolean isEmergency;
    private String emergencyMessage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Question {
        private String id;
        private String text;
        private String type;
        private Boolean required;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectedData {
        private String chiefComplaint;
        private Boolean durationKnown;
    }
}
