package com.example.common.pagination;

import java.util.List;
import java.util.function.Function;

public record SliceResponse<T>(
	List<T> content,
	Long nextCursor,
	boolean hasNext
) {
	public static <T> SliceResponse<T> of(List<T> content, Long nextCursor) {
		return new SliceResponse<>(content, nextCursor, nextCursor != null);
	}

	public static <T> SliceResponse<T> empty() {
		return new SliceResponse<>(List.of(), null, false);
	}

	public <R> SliceResponse<R> map(Function<T, R> mapper) {
		List<R> mappedContent = content.stream()
			.map(mapper)
			.toList();
		return new SliceResponse<>(mappedContent, nextCursor, hasNext);
	}
}
