package com.multichat.message.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Defaults intentionally retain message bodies for three calendar months. */
@ConfigurationProperties(prefix = "chat.retention.messages")
public class MessageRetentionProperties {
    private boolean enabled = true;
    private int retentionMonths = 3;
    private int batchSize = 500;
    private int maxBatchRetries = 3;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getRetentionMonths() { return retentionMonths; }
    public void setRetentionMonths(int retentionMonths) { this.retentionMonths = retentionMonths; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getMaxBatchRetries() { return maxBatchRetries; }
    public void setMaxBatchRetries(int maxBatchRetries) { this.maxBatchRetries = maxBatchRetries; }
}
