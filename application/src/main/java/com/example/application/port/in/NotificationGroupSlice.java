package com.example.application.port.in;

import java.util.List;
import java.util.function.Function;

public record NotificationGroupSlice<T>(
	List<T> items,
	boolean hasNext,
	Long nextCursorId
) {
	public static <T> NotificationGroupSlice<T> of(
		List<T> fetched,
		int requestedSize,
		Function<T, Long> idExtractor
	) {
		boolean hasNext = fetched.size() > requestedSize;
		List<T> items = List.copyOf(
			hasNext ? fetched.subList(0, requestedSize) : fetched
		);
		return new NotificationGroupSlice<>(
			items,
			hasNext,
			hasNext ? idExtractor.apply(items.getLast()) : null
		);
	}
}
