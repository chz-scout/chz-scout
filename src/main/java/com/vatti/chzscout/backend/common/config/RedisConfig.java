package com.vatti.chzscout.backend.common.config;

import org.springframework.context.annotation.Configuration;

/**
 * Redis 설정.
 *
 * <p>StringRedisTemplate은 Spring Boot가 자동 구성하므로 별도 Bean 정의 불필요. 각 Store에서 JsonMapper를 사용하여 직접
 * 직렬화/역직렬화 처리.
 */
@Configuration
public class RedisConfig {}
