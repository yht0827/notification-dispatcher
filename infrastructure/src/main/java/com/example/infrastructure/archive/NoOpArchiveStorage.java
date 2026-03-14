package com.example.infrastructure.archive;

import com.example.application.port.out.ArchiveStorage;

import lombok.extern.slf4j.Slf4j;

/**
 * S3 미연동 시 사용하는 NoOp 구현체.
 * 추후 S3ArchiveStorage로 교체합니다.
 */
@Slf4j
public class NoOpArchiveStorage implements ArchiveStorage {

	@Override
	public void export(String tableName, String partitionName) {
		// S3 미연동 - 추후 구현
		log.info("Archive export skipped (no storage configured): table={}, partition={}", tableName, partitionName);
	}
}
