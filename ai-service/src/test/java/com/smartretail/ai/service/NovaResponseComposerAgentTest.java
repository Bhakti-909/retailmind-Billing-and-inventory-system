package com.smartretail.ai.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NovaResponseComposerAgentTest {
	@Test
	void retriesOnlyBeforeFirstTokenAndStreamsFallbackExactlyOnce() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		List<String> requestBodies = new ArrayList<>();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/api/generate", exchange -> {
			requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			if (requests.incrementAndGet() == 1) {
				byte[] missing = "{\"error\":\"model 'fast' not found\"}".getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(404, missing.length);
				try (var output = exchange.getResponseBody()) { output.write(missing); }
				return;
			}
			byte[] body = "{\"thinking\":\"private thought\",\"response\":\"verified\",\"done\":false}\n{\"response\":\" result\",\"done\":true}\n".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
			exchange.sendResponseHeaders(200, body.length);
			try (var output = exchange.getResponseBody()) { output.write(body); }
		});
		server.start();
		try {
			NovaResponseComposerAgent composer = new NovaResponseComposerAgent(RestClient.builder(), url(server), "reasoning", "fast", "reasoning", 5);
			List<String> tokens = new ArrayList<>();
			composer.composeStream("grounded prompt", "fast", tokens::add);
			assertEquals(2, requests.get());
			assertEquals(List.of("verified", " result"), tokens);
			org.junit.jupiter.api.Assertions.assertTrue(requestBodies.get(0).contains("\"model\":\"fast\""));
			org.junit.jupiter.api.Assertions.assertTrue(requestBodies.get(0).contains("\"think\":false"));
			org.junit.jupiter.api.Assertions.assertTrue(requestBodies.get(0).contains("\"num_predict\":192"));
			org.junit.jupiter.api.Assertions.assertTrue(requestBodies.get(1).contains("\"model\":\"reasoning\""));
			org.junit.jupiter.api.Assertions.assertTrue(requestBodies.get(1).contains("\"think\":true"));
			org.junit.jupiter.api.Assertions.assertTrue(requestBodies.get(1).contains("\"num_predict\":384"));
		} finally { server.stop(0); }
	}

	@Test
	void doesNotFallbackForTransientModelFailure() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		HttpServer server = server(exchange -> {
			requests.incrementAndGet();
			exchange.sendResponseHeaders(503, -1);
		});
		try {
			NovaResponseComposerAgent composer = composer(server);
			assertThrows(IllegalStateException.class, () -> composer.composeStream("prompt", "fast", ignored -> { }));
			assertEquals(1, requests.get());
		} finally { server.stop(0); }
	}

	@Test
	void rejectsThinkingOnlyStreamWithoutForwardingThinkingOrRetrying() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		HttpServer server = server(exchange -> {
			requests.incrementAndGet();
			write(exchange, 200, "{\"thinking\":\"internal reasoning\",\"response\":\"\",\"done\":false}\n{\"done\":true}\n");
		});
		try {
			NovaResponseComposerAgent composer = composer(server);
			List<String> tokens = new ArrayList<>();
			assertThrows(IllegalStateException.class, () -> composer.composeStream("prompt", "reasoning", tokens::add));
			assertEquals(1, requests.get());
			assertEquals(List.of(), tokens);
		} finally { server.stop(0); }
	}

	@Test
	void rejectsStreamThatNeverSendsDoneEvenIfItHasPartialText() throws Exception {
		HttpServer server = server(exchange -> write(exchange, 200, "{\"response\":\"partial\",\"done\":false}\n"));
		try {
			NovaResponseComposerAgent composer = composer(server);
			List<String> tokens = new ArrayList<>();
			assertThrows(IllegalStateException.class, () -> composer.composeStream("prompt", "reasoning", tokens::add));
			assertEquals(List.of("partial"), tokens);
		} finally { server.stop(0); }
	}

	@Test
	void returnsImmediatelyAtDoneEvenWhileOllamaKeepsTheHttpBodyOpen() throws Exception {
		CountDownLatch allowServerClose = new CountDownLatch(1);
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/api/generate", exchange -> {
			exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
			exchange.sendResponseHeaders(200, 0);
			try (var output = exchange.getResponseBody()) {
				output.write("{\"response\":\"answer\",\"done\":true}\n".getBytes(StandardCharsets.UTF_8));
				output.flush();
				allowServerClose.await(5, TimeUnit.SECONDS);
			} catch (InterruptedException error) {
				Thread.currentThread().interrupt();
			}
		});
		server.start();
		try {
			NovaResponseComposerAgent composer = composer(server);
			List<String> tokens = new ArrayList<>();
			long startedAt = System.nanoTime();
			composer.composeStream("prompt", "reasoning", tokens::add);
			long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
			assertEquals(List.of("answer"), tokens);
			org.junit.jupiter.api.Assertions.assertTrue(elapsedMs < 1500,
				"Composer waited for Ollama's HTTP body to close: " + elapsedMs + " ms");
		} finally {
			allowServerClose.countDown();
			server.stop(0);
		}
	}

	@Test
	void rejectsCompletedStreamWithEmptyAnswer() throws Exception {
		HttpServer server = server(exchange -> write(exchange, 200, "{\"response\":\"\",\"done\":true}\n"));
		try {
			NovaResponseComposerAgent composer = composer(server);
			assertThrows(IllegalStateException.class, () -> composer.composeStream("prompt", "reasoning", ignored -> { }));
		} finally { server.stop(0); }
	}

	@Test
	void rejectsOutputTruncatedByTheTokenLimit() throws Exception {
		HttpServer server = server(exchange -> write(exchange, 200,
			"{\"response\":\"unfinished\",\"done\":true,\"done_reason\":\"length\"}\n"));
		try {
			NovaResponseComposerAgent composer = composer(server);
			List<String> tokens = new ArrayList<>();
			assertThrows(IllegalStateException.class, () -> composer.composeStream("prompt", "reasoning", tokens::add));
			assertEquals(List.of("unfinished"), tokens);
		} finally { server.stop(0); }
	}

	@Test
	void neverRetriesAfterPartialTokensHaveBeenEmitted() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/api/generate", exchange -> {
			requests.incrementAndGet();
			byte[] body = "{\"response\":\"partial\",\"done\":false}\n{\"error\":\"generation failed\"}\n".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			try (var output = exchange.getResponseBody()) { output.write(body); }
		});
		server.start();
		try {
			NovaResponseComposerAgent composer = new NovaResponseComposerAgent(RestClient.builder(), url(server), "reasoning", "fast", "reasoning", 5);
			List<String> tokens = new ArrayList<>();
			assertThrows(IllegalStateException.class, () -> composer.composeStream("grounded prompt", "fast", tokens::add));
			assertEquals(1, requests.get());
			assertEquals(List.of("partial"), tokens);
		} finally { server.stop(0); }
	}

	@Test
	void usesTheFastModelForGroundedReportsCalculationsAndGrowthPlans() {
		NovaResponseComposerAgent composer = new NovaResponseComposerAgent(
			RestClient.builder(), "http://127.0.0.1:11434", "reasoning", "fast", "reasoning", 5);
		NovaIntentClassifier classifier = new NovaIntentClassifier();

		assertEquals("fast", composer.selectModel(classifier.classify("generate a today report", List.of())));
		assertEquals("fast", composer.selectModel(classifier.classify("today business loss and profit calculate", List.of())));
		assertEquals("fast", composer.selectModel(classifier.classify("cover today's loss within 7 days", List.of())));
		assertEquals("reasoning", composer.selectModel(classifier.classify("forecast supplier demand and compare the trends", List.of())));
	}

	private String url(HttpServer server) { return "http://127.0.0.1:" + server.getAddress().getPort(); }
	private NovaResponseComposerAgent composer(HttpServer server) {
		return new NovaResponseComposerAgent(RestClient.builder(), url(server), "reasoning", "fast", "reasoning", 5);
	}
	private HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/api/generate", handler);
		server.start();
		return server;
	}
	private void write(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
		exchange.sendResponseHeaders(status, bytes.length);
		try (var output = exchange.getResponseBody()) { output.write(bytes); }
	}
}
