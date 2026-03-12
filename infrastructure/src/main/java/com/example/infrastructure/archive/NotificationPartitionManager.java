package com.example.infrastructure.archive;

import java.time.YearMonth;

import org.springframework.jdbc.core.JdbcTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class NotificationPartitionManager {

	private final JdbcTemplate jdbcTemplate;

	public void ensureNextMonthPartitions() {
		// 다음 달 파티션을 미리 생성 (월초 스케줄러 실행)
		YearMonth nextMonth = YearMonth.now().plusMonths(1);
		ensureNextMonthPartition("notification_archive", nextMonth);
		ensureNextMonthPartition("notification_group_archive", nextMonth);
		ensureNextMonthPartition("notification_read_status_archive", nextMonth);
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

	private String partitionName(YearMonth yearMonth) {
		// 파티션명 형식: p202501
		return "p%04d%02d".formatted(yearMonth.getYear(), yearMonth.getMonthValue());
	}

	private int partitionValue(YearMonth yearMonth) {
		// LESS THAN 비교용 정수값: 202501 형태
		return yearMonth.getYear() * 100 + yearMonth.getMonthValue();
	}
}
