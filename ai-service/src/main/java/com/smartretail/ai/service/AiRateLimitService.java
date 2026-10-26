package com.smartretail.ai.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/** Shared per-business chat quota, so limits remain effective across AI service replicas. */
@Service
public class AiRateLimitService {
	private static final int WINDOW_SECONDS = 60;
	private static final DefaultRedisScript<Long> INCREMENT_WINDOW = new DefaultRedisScript<>(
		"local count = redis.call('INCR', KEYS[1]); "
			+ "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; "
			+ "return count;",
		Long.class);

	private final StringRedisTemplate redis;

	public AiRateLimitService(StringRedisTemplate redis) { this.redis = redis; }

	public long recordChatRequest(Long businessId) {
		Long count = redis.execute(INCREMENT_WINDOW,
			List.of("nova:rate-limit:chat:business:" + businessId),
			Integer.toString(WINDOW_SECONDS));
		if (count == null) throw new IllegalStateException("The AI request limit service is unavailable.");
		return count;
	}
}
