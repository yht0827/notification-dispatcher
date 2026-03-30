package com.example.infrastructure.config.datasource;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Master/Replica DataSource 라우팅 설정.
 * datasource.routing.enabled=true 일 때만 활성화되며,
 * @Transactional(readOnly=true) 요청은 Replica, 나머지는 Master로 라우팅한다.
 *
 * false(기본값)이면 Spring Boot 자동 구성 단일 DataSource를 사용한다.
 */
@Configuration
@ConditionalOnProperty(name = "datasource.routing.enabled", havingValue = "true")
public class DataSourceRoutingConfig {

	/**
	 * Master DataSource: spring.datasource.* + spring.datasource.hikari.* 설정을 사용한다.
	 */
	@Bean("masterDataSource")
	@ConfigurationProperties("spring.datasource.hikari")
	public HikariDataSource masterDataSource(DataSourceProperties dataSourceProperties) {
		return dataSourceProperties.initializeDataSourceBuilder()
			.type(HikariDataSource.class)
			.build();
	}

	/**
	 * Slave DataSource: datasource.replica.* 설정을 사용한다.
	 */
	@Bean("replicaDataSource")
	public DataSource replicaDataSource(
		@Value("${datasource.replica.url}") String url,
		@Value("${datasource.replica.username}") String username,
		@Value("${datasource.replica.password}") String password,
		@Value("${datasource.replica.maximum-pool-size:10}") int maxPoolSize,
		@Value("${datasource.replica.minimum-idle:5}") int minIdle
	) {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(url);
		config.setUsername(username);
		config.setPassword(password);
		config.setDriverClassName("com.mysql.cj.jdbc.Driver");
		config.setMaximumPoolSize(maxPoolSize);
		config.setMinimumIdle(minIdle);
		config.setConnectionTimeout(5000);
		config.setPoolName("replica-pool");
		return new HikariDataSource(config);
	}

	/**
	 * LazyConnectionDataSourceProxy로 감싸 트랜잭션 시작 후 커넥션을 획득하도록 한다.
	 * readOnly=true → Slave, readOnly=false → Master
	 */
	@Bean
	@Primary
	public DataSource dataSource(
		@Qualifier("masterDataSource") DataSource master,
		@Qualifier("replicaDataSource") DataSource replica
	) {
		RoutingDataSource routing = new RoutingDataSource();
		routing.setTargetDataSources(Map.of("master", master, "replica", replica));
		routing.setDefaultTargetDataSource(master);
		routing.afterPropertiesSet();
		return new LazyConnectionDataSourceProxy(routing);
	}
}
