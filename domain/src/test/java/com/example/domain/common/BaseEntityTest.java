package com.example.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaseEntityTest {

	@Test
	@DisplayName("삭제 전에는 isDeleted가 false이고 delete 후에는 true다")
	void delete_marksEntityAsDeleted() {
		TestEntity entity = new TestEntity();

		assertThat(entity.isDeleted()).isFalse();

		entity.delete();

		assertThat(entity.isDeleted()).isTrue();
	}

	private static final class TestEntity extends BaseEntity {
	}
}
