package com.example.infrastructure.archive;

import java.time.YearMonth;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

import com.example.application.port.out.ArchiveStorage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class NotificationPartitionManager {

	private static final List<String> ARCHIVE_TABLES = List.of(
		"notification_archive",
		"notification_group_archive",
		"notification_read_status_archive"
	);

	private final JdbcTemplate jdbcTemplate;
	private final ArchiveProperties archiveProperties;
	private final ArchiveStorage archiveStorage;

	public void ensureNextMonthPartitions() {
		// 다음 달 파티션을 미리 생성 (월초 스케줄러 실행)
		YearMonth nextMonth = YearMonth.now().plusMonths(1);
		for (String tableName : ARCHIVE_TABLES) {
			ensureNextMonthPartition(tableName, nextMonth);
		}
	}

	public void dropOldPartitions() {
		// 보존 기간(partition-retention-months) 이전 파티션 DROP
		YearMonth cutoff = YearMonth.now().minusMonths(archiveProperties.resolvePartitionRetentionMonths());
		String cutoffPartitionName = partitionName(cutoff);

		for (String tableName : ARCHIVE_TABLES) {
			List<String> oldPartitions = findPartitionsOlderThan(tableName, cutoffPartitionName);
			for (String partitionName : oldPartitions) {
				// S3 export 후 DROP (NoOp → 추후 S3ArchiveStorage로 교체)
				archiveStorage.export(tableName, partitionName);
				dropPartition(tableName, partitionName);
			}
		}
	}

	private void ensureNextMonthPartition(String tableName, YearMonth nextMonth) {
		String partitionName = partitionName(nextMonth);
		// information_schema로 파티션 존재 여부 확인
		Integer exists = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.PARTITIONS
				WHERE TABLE_SCHEMA = DATABASE()
				  AND TABLE_NAME = ?
				  AND PARTITION_NAME = ?
				""",
			Integer.class,
			tableName,
			partitionName
		);

		if (exists != null && exists > 0) {
			return;
		}

		// p_future 파티션을 다음 달 파티션 + 새 p_future로 분할
		int lessThan = partitionValue(nextMonth.plusMonths(1));
		String sql = """
			ALTER TABLE %s
			REORGANIZE PARTITION p_future INTO (
			    PARTITION %s VALUES LESS THAN (%d),
			    PARTITION p_future VALUES LESS THAN MAXVALUE
			)
			""".formatted(tableName, partitionName, lessThan);
		jdbcTemplate.execute(sql);
		log.info("Archive partition 생성: table={}, partition={}", tableName, partitionName);
	}

	private List<String> findPartitionsOlderThan(String tableName, String cutoffPartitionName) {
		// cutoff 파티션명보다 작은(이전) 파티션 목록 조회 (p_future 제외)
		return jdbcTemplate.queryForList("""
				SELECT PARTITION_NAME
				FROM information_schema.PARTITIONS
				WHERE TABLE_SCHEMA = DATABASE()
				  AND TABLE_NAME = ?
				  AND PARTITION_NAME != 'p_future'
				  AND PARTITION_NAME < ?
				ORDER BY PARTITION_NAME
				""",
			String.class,
			tableName,
			cutoffPartitionName
		);
	}

	private void dropPartition(String tableName, String partitionName) {
		jdbcTemplate.execute("ALTER TABLE %s DROP PARTITION %s".formatted(tableName, partitionName));
		log.info("Archive partition DROP: table={}, partition={}", tableName, partitionName);
	}

	private String partitionName(YearMonth yearMonth) {
		// 파티션명 형식: p202501
		return "p%04d%02d".formatted(yearMonth.getYear(), yearMonth.getMonthValue());
	}

	private int partitionValue(YearMonth yearMonth) {
		// LESS THAN 비교용 정수값: 202501 형태
		return yearMonth.getYear() * 100 + yearMonth.getMonthValue();
	}
}
