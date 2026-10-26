package com.smartretail.ai.controller;

import com.smartretail.ai.service.AiAgentService;
import com.smartretail.ai.service.AiRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.List;
import org.springframework.core.task.AsyncTaskExecutor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.RejectedExecutionException;

@RestController
@RequestMapping("/api/ai")
public class AiController {
	private final AiAgentService agent;
	private final AiRateLimitService rateLimits;
	private final AsyncTaskExecutor streamExecutor;
	public AiController(AiAgentService agent, AiRateLimitService rateLimits,
			@Qualifier("novaStreamExecutor") AsyncTaskExecutor streamExecutor) {
		this.agent = agent;
		this.rateLimits = rateLimits;
		this.streamExecutor = streamExecutor;
	}
	@PostMapping("/chat")
	public Map<String, Object> chat(@RequestBody ChatRequest request, HttpServletRequest httpRequest) {
		Long businessId = (Long) httpRequest.getAttribute("businessId");
		validateRequest(request, businessId);
		enforceChatRateLimit(businessId);
		try { return agent.ask(businessId, request.message(), request.history() == null ? List.of() : request.history()); }
		catch (IllegalArgumentException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage()); }
		catch (IllegalStateException error) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error.getMessage()); }
	}

	@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter chatStream(@RequestBody ChatRequest request, HttpServletRequest httpRequest) {
		Long businessId = (Long) httpRequest.getAttribute("businessId");
		validateRequest(request, businessId);
		enforceChatRateLimit(businessId);
		SseEmitter emitter = createEmitter();
		AtomicBoolean completedNormally = new AtomicBoolean(false);
		try {
			Future<?> task = streamExecutor.submit(() -> {
			try {
				agent.askStreaming(businessId, request.message(), request.history() == null ? List.of() : request.history(), token -> {
					try {
						emitter.send(SseEmitter.event().name("token").data(token));
					} catch (java.io.IOException error) {
						throw new IllegalStateException("The client disconnected while Nova was responding.", error);
					}
				}, reportPath -> {
					try {
						emitter.send(SseEmitter.event().name("report").data(reportPath));
					} catch (java.io.IOException error) {
						throw new IllegalStateException("The client disconnected while Nova was responding.", error);
					}
				});
				// The generation work is finished before the terminal frame reaches
				// the browser, so a fast client-side reader cancel must not interrupt
				// this task while it is closing the SSE response.
				completedNormally.set(true);
				emitter.send(SseEmitter.event().name("done").data("complete"));
				emitter.complete();
			} catch (Exception error) {
				if (Thread.currentThread().isInterrupted()) return;
				try {
					emitter.send(SseEmitter.event().name("error").data(
						error.getMessage() == null ? "Nova could not complete the response." : error.getMessage()));
				} catch (Exception ignored) {
					// The browser may have closed the stream while the error was being sent.
				}
				emitter.completeWithError(error);
			}
			});
			emitter.onTimeout(() -> task.cancel(true));
			emitter.onError(error -> task.cancel(true));
			emitter.onCompletion(() -> { if (!completedNormally.get()) task.cancel(true); });
		} catch (RejectedExecutionException error) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Nova is busy. Please try again shortly.");
		}
		return emitter;
	}

	protected SseEmitter createEmitter() { return new SseEmitter(240_000L); }

	private void enforceChatRateLimit(Long businessId) {
		try {
			if (rateLimits.recordChatRequest(businessId) > 30)
				throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
					"Nova allows 30 requests per business each minute. Please wait and try again.");
		} catch (ResponseStatusException error) {
			throw error;
		} catch (RuntimeException error) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
				"The AI request limit service is unavailable. Please retry shortly.");
		}
	}

	private void validateRequest(ChatRequest request, Long businessId) {
		if (businessId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Business context is missing.");
		if (request == null || request.message() == null || request.message().isBlank() || request.message().length() > 2000)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a question between 1 and 2000 characters.");
		if (request.history() != null && request.history().size() > 8)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conversation context can contain at most 8 recent messages.");
	}
	public record ChatRequest(String message, List<AiAgentService.ChatTurn> history) { }
}
