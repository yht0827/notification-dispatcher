package com.example.application.port.out;

/**
 * 아카이브 데이터 외부 저장소 인터페이스.
 * 파티션 DROP 전 데이터를 외부 저장소(S3 등)로 export합니다.
 */
public interface ArchiveStorage {

	void export(String tableName, String partitionName);
}
