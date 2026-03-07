package com.example.domain.exception;

public class UnsupportedOutboxTypeException extends DomainException {

	public UnsupportedOutboxTypeException(String typeName, String value, String supportedValues) {
		super(String.format("지원하지 않는 %s입니다: %s. 지원 값: %s", typeName, value, supportedValues));
	}
}
