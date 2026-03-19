package com.example.infrastructure.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

public class DockerAvailabilityCondition implements ExecutionCondition {

	private static final ConditionEvaluationResult ENABLED =
		ConditionEvaluationResult.enabled("Docker is available");

	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
		try {
			if (DockerClientFactory.instance().isDockerAvailable()) {
				return ENABLED;
			}
			return ConditionEvaluationResult.disabled("Docker is not available");
		} catch (Throwable throwable) {
			return ConditionEvaluationResult.disabled("Docker is not available: " + throwable.getMessage());
		}
	}
}
