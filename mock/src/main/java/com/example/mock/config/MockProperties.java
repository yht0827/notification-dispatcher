package com.example.mock.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "mock")
public class MockProperties {

    @NotNull
    private MockMode mode = MockMode.RANDOM;

    private final Latency latency = new Latency();
    private final Failure failure = new Failure();
    private final Log log = new Log();

    public MockMode getMode() { return mode; }
    public void setMode(MockMode mode) { this.mode = mode; }
    public Latency getLatency() { return latency; }
    public Failure getFailure() { return failure; }
    public Log getLog() { return log; }

    public static class Latency {
        private boolean enabled = true;
        private double probability = 0.15d;
        @Min(0) private int minMs = 300;
        @Min(1) private int maxMs = 2500;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getProbability() { return probability; }
        public void setProbability(double probability) { this.probability = probability; }
        public int getMinMs() { return minMs; }
        public void setMinMs(int minMs) { this.minMs = minMs; }
        public int getMaxMs() { return maxMs; }
        public void setMaxMs(int maxMs) { this.maxMs = maxMs; }
    }

    public static class Failure {
        private boolean enabled = true;
        private double probability = 0.10d;
        private List<Integer> types = new ArrayList<>(List.of(500, 503, 429));

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getProbability() { return probability; }
        public void setProbability(double probability) { this.probability = probability; }
        public List<Integer> getTypes() { return types; }
        public void setTypes(List<Integer> types) { this.types = types; }
    }

    public static class Log {
        private boolean includeMaskedMessagePreview = false;
        @Min(1) @Max(200) private int messagePreviewLength = 20;

        public boolean isIncludeMaskedMessagePreview() { return includeMaskedMessagePreview; }
        public void setIncludeMaskedMessagePreview(boolean v) { this.includeMaskedMessagePreview = v; }
        public int getMessagePreviewLength() { return messagePreviewLength; }
        public void setMessagePreviewLength(int messagePreviewLength) { this.messagePreviewLength = messagePreviewLength; }
    }
}
