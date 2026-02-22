package com.example.common.pagination;

public record CursorRequest(
	Long cursor,
	int size
) {
	private static final int DEFAULT_SIZE = 20;
	private static final int MAX_SIZE = 100;

	public CursorRequest {
		if (size <= 0) {
			size = DEFAULT_SIZE;
		}
		if (size > MAX_SIZE) {
			size = MAX_SIZE;
		}
	}

	public static CursorRequest of(Long cursor, Integer size) {
		return new CursorRequest(cursor, size != null ? size : DEFAULT_SIZE);
	}

	public boolean hasKey() {
		return cursor != null;
	}
}
