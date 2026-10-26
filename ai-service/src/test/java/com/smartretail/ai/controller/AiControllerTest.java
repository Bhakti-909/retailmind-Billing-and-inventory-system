package com.smartretail.ai.controller;

import com.smartretail.ai.service.AiAgentService;
import com.smartretail.ai.service.AiRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiControllerTest {
	@Test
	void dispatchesOneStreamingRequestAndEachTokenOnce() throws InterruptedException {
		AiAgentService agent = mock(AiAgentService.class);
		AiRateLimitService limits = mock(AiRateLimitService.class);
		when(limits.recordChatRequest(9L)).thenReturn(1L);
		AtomicInteger sentEvents = new AtomicInteger();
		AtomicInteger completedStreams = new AtomicInteger();
		CountDownLatch complete = new CountDownLatch(1);
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.initialize();
		AiController controller = new AiController(agent, limits, executor) {
			@Override protected SseEmitter createEmitter() {
				return new SseEmitter() {
					@Override public void send(SseEventBuilder builder) throws IOException {
						if (sentEvents.incrementAndGet() == 4) complete.countDown();
					}
					@Override public void complete() {
						completedStreams.incrementAndGet();
						super.complete();
					}
				};
			}
		};
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getAttribute("businessId")).thenReturn(9L);
		doAnswer(invocation -> {
			@SuppressWarnings("unchecked") Consumer<String> onToken = invocation.getArgument(3);
			@SuppressWarnings("unchecked") Consumer<String> onReport = invocation.getArgument(4);
			onToken.accept("A");
			onToken.accept("B");
			onReport.accept("/reports?from=2026-10-01&to=2026-10-11");
			return null;
		}).when(agent).askStreaming(eq(9L), eq("give me a report"), anyList(), any(), any());

		controller.chatStream(new AiController.ChatRequest("give me a report", null), request);
		org.junit.jupiter.api.Assertions.assertTrue(complete.await(5, TimeUnit.SECONDS));
		executor.shutdown();

		verify(agent).askStreaming(eq(9L), eq("give me a report"), anyList(), any(), any());
		assertEquals(4, sentEvents.get()); // two tokens, one report action, one completion event
		assertEquals(1, completedStreams.get());
	}
}
