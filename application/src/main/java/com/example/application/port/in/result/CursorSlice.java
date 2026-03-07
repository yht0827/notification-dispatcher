package com.example.application.port.in.result;

import java.util.List;
import java.util.function.Function;

public record CursorSlice<T>(
	List<T> items,
	boolean hasNext,
	Long nextCursorId
) {
	public static <T> CursorSlice<T> of(
		List<T> fetched,
		int requestedSize,
		Function<T, Long> idExtractor
	) {
		if (requestedSize < 1) {
			throw new IllegalArgumentException("requestedSize must be greater than 0");
		}

		boolean hasNext = fetched.size() > requestedSize;
		List<T> items = List.copyOf(
			hasNext ? fetched.subList(0, requestedSize) : fetched
		);
		return new CursorSlice<>(
			items,
			hasNext,
			hasNext ? idExtractor.apply(items.getLast()) : null
		);
	}
}
