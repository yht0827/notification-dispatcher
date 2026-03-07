package com.example.application.port.in.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CursorSliceTest {

	@Test
	@DisplayName("요청 크기가 1 미만이면 예외를 던진다")
	void of_throwsWhenRequestedSizeIsLessThanOne() {
		assertThatThrownBy(() -> CursorSlice.of(List.of(1L), 0, id -> id))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("requestedSize must be greater than 0");
	}

	@Test
	@DisplayName("요청 크기보다 하나 더 조회된 경우 hasNext와 nextCursorId를 계산한다")
	void of_trimsAndBuildsCursorMetadata() {
		record Item(Long id) {
		}

		CursorSlice<Item> slice = CursorSlice.of(
			List.of(new Item(30L), new Item(20L), new Item(10L)),
			2,
			Item::id
		);

		assertThat(slice.items()).extracting(Item::id).containsExactly(30L, 20L);
		assertThat(slice.hasNext()).isTrue();
		assertThat(slice.nextCursorId()).isEqualTo(20L);
	}
}
