package com.example.domain.exception;

public class InvalidStatusTransitionException extends DomainException {

	public InvalidStatusTransitionException(String message) {
		super(message);
	}

	public InvalidStatusTransitionException(Enum<?> currentStatus, Enum<?> targetStatus) {
		super(String.format("상태를 %s에서 %s로 변경할 수 없습니다.", currentStatus, targetStatus));
	}
}
