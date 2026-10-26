package com.smartretail.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;


@Configuration
public class AiWorkerConfig {
	@Bean(name = "novaStreamExecutor")
	public ThreadPoolTaskExecutor novaStreamExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(12);
		executor.setQueueCapacity(32);
		executor.setThreadNamePrefix("nova-stream-");
		executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
		executor.initialize();
		return executor;
	}
}
