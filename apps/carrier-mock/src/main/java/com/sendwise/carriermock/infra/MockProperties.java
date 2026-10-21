package com.sendwise.carriermock.infra;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "carrier-mock")
public class MockProperties {

    private String callbackBaseUrl = "http://localhost:8080";
    private long randomSeed = 1L;
    private String codesPath = "";
    private Map<String, CarrierProps> carriers = new LinkedHashMap<>();
    private List<TemplateProps> templates = new ArrayList<>();

    public String getCallbackBaseUrl() {
        return callbackBaseUrl;
    }

    public void setCallbackBaseUrl(String callbackBaseUrl) {
        this.callbackBaseUrl = callbackBaseUrl;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    public void setRandomSeed(long randomSeed) {
        this.randomSeed = randomSeed;
    }

    public String getCodesPath() {
        return codesPath;
    }

    public void setCodesPath(String codesPath) {
        this.codesPath = codesPath;
    }

    public Map<String, CarrierProps> getCarriers() {
        return carriers;
    }

    public void setCarriers(Map<String, CarrierProps> carriers) {
        this.carriers = carriers;
    }

    public List<TemplateProps> getTemplates() {
        return templates;
    }

    public void setTemplates(List<TemplateProps> templates) {
        this.templates = templates;
    }

    public static class CarrierProps {

        private int maxTps;
        private long callbackDelayMillis;
        private double callbackLossRatio;
        private LatencyProps latency = new LatencyProps();
        private Map<String, Double> failureRatio = new LinkedHashMap<>();

        public int getMaxTps() {
            return maxTps;
        }

        public void setMaxTps(int maxTps) {
            this.maxTps = maxTps;
        }

        public long getCallbackDelayMillis() {
            return callbackDelayMillis;
        }

        public void setCallbackDelayMillis(long callbackDelayMillis) {
            this.callbackDelayMillis = callbackDelayMillis;
        }

        public double getCallbackLossRatio() {
            return callbackLossRatio;
        }

        public void setCallbackLossRatio(double callbackLossRatio) {
            this.callbackLossRatio = callbackLossRatio;
        }

        public LatencyProps getLatency() {
            return latency;
        }

        public void setLatency(LatencyProps latency) {
            this.latency = latency;
        }

        public Map<String, Double> getFailureRatio() {
            return failureRatio;
        }

        public void setFailureRatio(Map<String, Double> failureRatio) {
            this.failureRatio = failureRatio;
        }
    }

    public static class LatencyProps {

        private String distribution = "fixed";
        private long minMillis;
        private long maxMillis;

        public String getDistribution() {
            return distribution;
        }

        public void setDistribution(String distribution) {
            this.distribution = distribution;
        }

        public long getMinMillis() {
            return minMillis;
        }

        public void setMinMillis(long minMillis) {
            this.minMillis = minMillis;
        }

        public long getMaxMillis() {
            return maxMillis;
        }

        public void setMaxMillis(long maxMillis) {
            this.maxMillis = maxMillis;
        }
    }

    public static class TemplateProps {

        private String code;
        private String body;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }
    }
}
