package io.hwan.atlaskb.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private Prompt prompt = new Prompt();
    private Generation generation = new Generation();

    public Prompt getPrompt() {
        return prompt;
    }

    public void setPrompt(Prompt prompt) {
        this.prompt = prompt;
    }

    public Generation getGeneration() {
        return generation;
    }

    public void setGeneration(Generation generation) {
        this.generation = generation;
    }

    public static class Prompt {

        private String rules;
        private String refStart = "<<REF>>";
        private String refEnd = "<<END>>";
        private String noResultText = "（本轮无检索结果）";

        public String getRules() {
            return rules;
        }

        public void setRules(String rules) {
            this.rules = rules;
        }

        public String getRefStart() {
            return refStart;
        }

        public void setRefStart(String refStart) {
            this.refStart = refStart;
        }

        public String getRefEnd() {
            return refEnd;
        }

        public void setRefEnd(String refEnd) {
            this.refEnd = refEnd;
        }

        public String getNoResultText() {
            return noResultText;
        }

        public void setNoResultText(String noResultText) {
            this.noResultText = noResultText;
        }
    }

    public static class Generation {

        private Double temperature = 0.3;
        private Integer maxTokens = 2000;
        private Double topP = 0.9;

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public Double getTopP() {
            return topP;
        }

        public void setTopP(Double topP) {
            this.topP = topP;
        }
    }
}
