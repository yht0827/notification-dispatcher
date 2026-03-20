package com.example.domain.notification;

import jakarta.persistence.Embeddable;

@Embeddable
public class NotificationStats {

	private int totalCount;
	private int sentCount;
	private int failedCount;

	protected NotificationStats() {
		this.totalCount = 0;
		this.sentCount = 0;
		this.failedCount = 0;
	}

	public NotificationStats(int totalCount, int sentCount, int failedCount) {
		this.totalCount = totalCount;
		this.sentCount = sentCount;
		this.failedCount = failedCount;
	}

	public int getTotalCount() {
		return totalCount;
	}

	public int getSentCount() {
		return sentCount;
	}

	public int getFailedCount() {
		return failedCount;
	}

	public void initializeTotalCount(int totalCount) {
		this.totalCount = totalCount;
	}

	public void incrementTotal() {
		this.totalCount++;
	}

	public void incrementSent() {
		this.sentCount++;
	}

	public void incrementFailed() {
		this.failedCount++;
	}

	public int getPendingCount() {
		return totalCount - processedCount();
	}

	public boolean isCompleted() {
		return totalCount == processedCount();
	}

	private int processedCount() {
		return sentCount + failedCount;
	}
}
