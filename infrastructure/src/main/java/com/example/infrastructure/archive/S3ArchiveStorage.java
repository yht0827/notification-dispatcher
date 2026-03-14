package com.example.infrastructure.archive;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.springframework.jdbc.core.JdbcTemplate;

import com.example.application.port.out.ArchiveStorage;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@RequiredArgsConstructor
public class S3ArchiveStorage implements ArchiveStorage {

	private final JdbcTemplate jdbcTemplate;
	private final S3Client s3Client;
	private final S3ArchiveProperties s3Properties;
	private final ObjectMapper objectMapper;

	@Override
	public void export(String tableName, String partitionName) {
		log.info("S3 export 시작: table={}, partition={}", tableName, partitionName);

		List<Map<String, Object>> rows = queryPartition(tableName, partitionName);
		if (rows.isEmpty()) {
			log.info("S3 export 건너뜀 (데이터 없음): table={}, partition={}", tableName, partitionName);
			return;
		}

		String s3Key = buildS3Key(tableName, partitionName);
		byte[] data = serialize(rows, tableName, partitionName);

		s3Client.putObject(
			PutObjectRequest.builder()
				.bucket(s3Properties.bucket())
				.key(s3Key)
				.contentType("application/x-ndjson")
				.contentEncoding("gzip")
				.build(),
			RequestBody.fromBytes(data)
		);

		log.info("S3 export 완료: s3://{}/{}, rows={}", s3Properties.bucket(), s3Key, rows.size());
	}

	private List<Map<String, Object>> queryPartition(String tableName, String partitionName) {
		return jdbcTemplate.queryForList(
			"SELECT * FROM %s PARTITION (%s)".formatted(tableName, partitionName)
		);
	}

	private String buildS3Key(String tableName, String partitionName) {
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		return "%s/%s/%s/archive-%s.jsonl.gz".formatted(
			s3Properties.resolvePrefix(), tableName, partitionName, timestamp
		);
	}

	private byte[] serialize(List<Map<String, Object>> rows, String tableName, String partitionName) {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
			 GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
			for (Map<String, Object> row : rows) {
				gzip.write(objectMapper.writeValueAsBytes(row));
				gzip.write('\n');
			}
			gzip.finish();
			return baos.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(
				"S3 export 직렬화 실패: table=%s, partition=%s".formatted(tableName, partitionName), e
			);
		}
	}
}
