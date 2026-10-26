package com.smartretail.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartretail.ai.service.AiAgentService.ChatTurn;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Turns the grounded prompt into a complete answer or a stream of answer tokens. */
@Component
public class NovaResponseComposerAgent {
	private static final Logger log = LoggerFactory.getLogger(NovaResponseComposerAgent.class);
	private final RestClient ollama;
	private final URI generateUri;
	private final HttpClient streamingClient;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final String defaultModel;
	private final String fastModel;
	private final String reasoningModel;
	private final int timeoutSeconds;

	@Autowired
	public NovaResponseComposerAgent(RestClient.Builder builder,
			@org.springframework.beans.factory.annotation.Value("${ollama.base-url}") String ollamaUrl,
			@org.springframework.beans.factory.annotation.Value("${ollama.model}") String model,
			@org.springframework.beans.factory.annotation.Value("${ollama.fast-model:}") String fastModel,
			@org.springframework.beans.factory.annotation.Value("${ollama.reasoning-model:}") String reasoningModel,
			@org.springframework.beans.factory.annotation.Value("${ollama.timeout-seconds:180}") int timeoutSeconds) {
		this.defaultModel = model;
		this.fastModel = fastModel == null || fastModel.isBlank() ? model : fastModel.trim();
		this.reasoningModel = reasoningModel == null || reasoningModel.isBlank() ? model : reasoningModel.trim();
		this.timeoutSeconds = Math.max(1, timeoutSeconds);
		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
		requestFactory.setReadTimeout(Duration.ofSeconds(this.timeoutSeconds));
		this.ollama = builder.requestFactory(requestFactory).baseUrl(ollamaUrl).build();
		this.generateUri = URI.create(ollamaUrl.replaceAll("/$", "") + "/api/generate");
		this.streamingClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(3))
			.build();
	}

	/** Compatibility constructor for focused tests and direct construction. */
	public NovaResponseComposerAgent(RestClient.Builder builder, String ollamaUrl, String model, int timeoutSeconds) {
		this(builder, ollamaUrl, model, model, model, timeoutSeconds);
	}

	@SuppressWarnings("unchecked")
	public String compose(String prompt) {
		return compose(prompt, defaultModel);
	}

	public String compose(String prompt, String selectedModel) {
		try {
			return generate(prompt, selectedModel);
		} catch (ModelUnavailableException primaryError) {
			if (selectedModel.equals(reasoningModel)) throw primaryError;
			log.warn("Nova fast model is unavailable; attempting the configured reasoning model.");
			try {
				String answer = generate(prompt, reasoningModel);
				log.info("nova.stage name=model_fallback outcome=success");
				return answer;
			} catch (RuntimeException fallbackError) {
				log.info("nova.stage name=model_fallback outcome=failure");
				throw fallbackError;
			}
		}
	}

	@SuppressWarnings("unchecked")
	private String generate(String prompt, String selectedModel) {
		Map<String, Object> response;
		try {
			response = ollama.post().uri("/api/generate")
				.body(Map.of("model", selectedModel, "prompt", prompt, "stream", false,
					"think", selectedModel.equals(reasoningModel),
					"options", generationOptions(selectedModel)))
				.retrieve().body(Map.class);
		} catch (RestClientResponseException error) {
			if (isModelUnavailable(error.getStatusCode().value(), error.getResponseBodyAsString()))
				throw new ModelUnavailableException(selectedModel);
			throw new IllegalStateException("Ollama generation failed (HTTP " + error.getStatusCode().value() + ").", error);
		}
		Object text = response == null ? null : response.get("response");
		if (response != null && "length".equals(response.get("done_reason")))
			throw new IllegalStateException("Ollama reached its output limit before completing the answer.");
		if (!(text instanceof String answer) || answer.isBlank())
			throw new IllegalStateException("Ollama returned an empty answer.");
		return answer.trim();
	}

	public void composeStream(String prompt, Consumer<String> onToken) {
		composeStream(prompt, defaultModel, onToken);
	}

	public void composeStream(String prompt, String selectedModel, Consumer<String> onToken) {
		AtomicBoolean emitted = new AtomicBoolean(false);
		try {
			streamWithModel(prompt, selectedModel, token -> {
				emitted.set(true);
				onToken.accept(token);
			});
		} catch (ModelUnavailableException primaryError) {
			if (emitted.get() || selectedModel.equals(reasoningModel)) throw primaryError;
			log.warn("Nova fast model is unavailable before answer output; attempting the configured reasoning model.");
			try {
				streamWithModel(prompt, reasoningModel, onToken);
				log.info("nova.stage name=model_fallback outcome=success");
			} catch (RuntimeException fallbackError) {
				log.info("nova.stage name=model_fallback outcome=failure");
				throw fallbackError;
			}
		}
	}

	public String selectModel(NovaIntentClassifier.Classification classification) {
		Set<NovaIntentClassifier.Intent> intents = classification.intents();
		boolean forecastOnly = intents.contains(NovaIntentClassifier.Intent.FORECAST)
			&& intents.stream().allMatch(intent -> intent == NovaIntentClassifier.Intent.FORECAST
				|| intent == NovaIntentClassifier.Intent.SALES);
		boolean evidenceLedTask = intents.stream().allMatch(intent -> Set.of(
			NovaIntentClassifier.Intent.SALES, NovaIntentClassifier.Intent.PROFIT, NovaIntentClassifier.Intent.CALCULATION,
			NovaIntentClassifier.Intent.REPORT, NovaIntentClassifier.Intent.TODAY_REPORT,
			NovaIntentClassifier.Intent.GROWTH_ANALYSIS, NovaIntentClassifier.Intent.LOSS_RECOVERY,
			NovaIntentClassifier.Intent.CUSTOMER_RETENTION, NovaIntentClassifier.Intent.CUSTOMER_ACQUISITION,
			NovaIntentClassifier.Intent.COST_REDUCTION, NovaIntentClassifier.Intent.CUSTOMERS,
			NovaIntentClassifier.Intent.INVENTORY, NovaIntentClassifier.Intent.TOP_PRODUCTS,
			NovaIntentClassifier.Intent.SLOW_MOVING, NovaIntentClassifier.Intent.STOCK_ADVICE,
			NovaIntentClassifier.Intent.FORECAST).contains(intent));
		boolean complex = intents.size() > 1 && !forecastOnly && !evidenceLedTask;
		return complex ? reasoningModel : fastModel;
	}

	public String modelClass(String selectedModel) {
		return selectedModel.equals(reasoningModel) ? "reasoning" : "fast";
	}

	private void streamWithModel(String prompt, String selectedModel, Consumer<String> onToken) {
		try {
			String body = objectMapper.writeValueAsString(Map.of(
				"model", selectedModel,
				"prompt", prompt,
				"stream", true,
				"think", selectedModel.equals(reasoningModel),
				"options", generationOptions(selectedModel)));
			HttpRequest request = HttpRequest.newBuilder(generateUri)
				.timeout(Duration.ofSeconds(timeoutSeconds))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
			HttpResponse<java.io.InputStream> response = streamingClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				String errorBody;
				try (var errorStream = response.body()) {
					errorBody = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
				}
				if (isModelUnavailable(response.statusCode(), errorBody)) throw new ModelUnavailableException(selectedModel);
				throw new IllegalStateException("Ollama generation failed (HTTP " + response.statusCode() + ").");
			}
			boolean emitted = false;
			boolean completed = false;
			try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.isBlank()) continue;
					JsonNode frame = objectMapper.readTree(line);
					JsonNode token = frame.get("response");
					if (token != null && token.isTextual() && !token.asText().isEmpty()) {
						onToken.accept(token.asText());
						emitted = true;
					}
					if (frame.path("error").isTextual()) {
						String detail = frame.path("error").asText();
						if (isModelUnavailable(response.statusCode(), detail)) throw new ModelUnavailableException(selectedModel);
						throw new IllegalStateException("Ollama could not complete generation.");
					}
					if (frame.path("done").asBoolean(false)) {
						if ("length".equals(frame.path("done_reason").asText()))
							throw new IllegalStateException("Ollama reached its output limit before completing the answer.");
						completed = true;
						// Ollama's done frame is the protocol boundary. Do not wait for
						// the HTTP body/socket to close before returning the answer.
						break;
					}
				}
			}
			if (!completed) throw new IllegalStateException("Ollama stream ended before completion.");
			if (!emitted) throw new IllegalStateException("Ollama completed without answer text.");
		} catch (IllegalStateException error) {
			throw error;
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Nova response streaming was interrupted.", error);
		} catch (java.io.IOException error) {
			throw new IllegalStateException("Nova could not read the streamed response.", error);
		}
	}

	private Map<String, Object> generationOptions(String selectedModel) {
		// Qwen3 4B needs room for its separate thinking trace before emitting response text.
		int outputLimit = selectedModel.equals(reasoningModel) ? 384 : 160;
		return Map.of("temperature", 0.2, "num_ctx", 4096, "num_predict", outputLimit);
	}

	private boolean isModelUnavailable(int status, String responseBody) {
		return responseBody != null
			&& responseBody.toLowerCase(Locale.ROOT).contains("model")
			&& responseBody.toLowerCase(Locale.ROOT).contains("not found")
			&& (status == 404 || responseBody.startsWith("{") || responseBody.startsWith("\""));
	}

	private static final class ModelUnavailableException extends IllegalStateException {
		private ModelUnavailableException(String model) { super("Configured Ollama model is unavailable: " + model); }
	}


	public String buildPrompt(String question, Map<String, Object> live, List<String> docs, List<ChatTurn> history) {
		String lowerQuestion = question.toLowerCase(Locale.ROOT);
		String historyText = history.stream().map(ChatTurn::content).collect(java.util.stream.Collectors.joining(" ")).toLowerCase(Locale.ROOT);
		boolean currentCurrencySpecified = has(lowerQuestion, "inr", "rupee", "rupees", "rs.", "₹", "usd", "dollar", "dollars", "$");
		boolean wantsForeignCurrency = has(lowerQuestion, "usd", "dollar", "dollars", "$")
			|| (!currentCurrencySpecified && has(historyText, "usd", "dollar", "dollars", "$"));
		String currencyInstruction = wantsForeignCurrency
			? "Business amounts are stored in INR. No exchange-rate conversion tool is available, so do not convert INR amounts or label them as dollars; explain this briefly and keep the amount in INR. "
			: "Business amounts are stored in INR. Use \u20B9 or INR for business amounts unless the user asks for a different display preference; never change the symbol to imply an unperformed conversion. ";
		return "You are Nova, a professional retail business assistant. Answer the user's actual request directly, without an unnecessary greeting or introduction. Keep the final answer concise, never include private reasoning, and return only conclusions the user can use. Be concise and useful. Reply in the same language as the latest user message; understand English, Hindi, Marathi, common transliterations, spelling mistakes, and mixed-language requests. "
			+ "Use the signed-in business's provided records for questions about its products, sales, bills, customers, and suppliers. "
			+ "Never invent figures or use sample data for the user's business. If records are missing or a requested item has no match, say that clearly. "
			+ currencyInstruction
			+ "For date-specific sales questions use the matching selected-period summary; the daily sales series covers only the latest 28 days, and the business snapshot provides lifetime totals. "
			+ "For period comparisons, state both inclusive date windows and compare the supplied totals and absolute/percentage changes. If the comparison window has zero sales, say the percentage is unavailable. "
			+ "For general retail questions, use the provided retail guidance. Explain figures and offer a practical next step when useful. Forecasts are estimates, not guarantees. "
			+ "For growth analysis, tie each recommendation to a supplied metric and date window. Separate recorded facts, possible explanations, and forecasts. A pattern can suggest a cause but does not prove it. Give a practical next step and a measurable follow-up metric; never promise guaranteed growth. "
			+ "For loss recovery, follow the supplied lossRecoveryPlan. State whether the recorded loss is gross loss; operating expenses are not stored, so do not call it a net or operating loss. Treat required daily gross profit as a target, not a forecast or guarantee. Show a revenue equivalent only when the plan supplies one, and label it as an estimate based on observed positive gross margin. Recommend practical actions grounded in the supplied products, margins, stock, and sales; ask for expense data if the user means net loss. "
			+ "For customer retention or acquisition advice, use customerAnalysis.retentionSummary and recorded customer sales only. State the 90-day and 30-day windows, recommend measuring repeat-purchase rate or returning-customer count, and do not infer why a customer stopped buying. Never expose individual customer names unless the user asks for customer-specific data. "
			+ "For cost reduction, prioritize verified below-cost or low-margin products, slow-moving stock, and avoidable stock risk present in the supplied data. Do not invent operating expenses or claim savings without a recorded baseline. "
			+ "For a today's report, summarize only the selected date's sales, bills, items, and gross-profit data supplied. Distinguish gross profit from net profit; do not imply inventory or expense changes were made. "
			+ "For slow-moving products, use the supplied 28-day unit sales and current on-hand stock, describe these as review candidates, and do not infer why demand is low. For stock advice, use the supplied per-product recent sales and on-hand quantities. The estimate targets 14 days of cover from the last 28 days; explain this assumption and say when there is no sales history. "
			+ "For profit advice, use saved invoice revenue less cost of goods for gross profit. Gross profit is not net profit. Operating expenses are unavailable in the supplied business records; say net profit cannot be calculated and do not estimate expenses. For product gross margins, use the supplied discount-adjusted figures; identify low-margin products as review candidates and suggest checking their saved costs and discounts. You may recommend evidence-based next actions, but never claim a price, stock, supplier, or billing change was made unless an authorized existing write workflow confirms it. Require explicit user confirmation before any business-record change. "
		+ "Use the recent conversation only to resolve references such as 'it', 'that', 'and today?', or a follow-up date. The newest user message is the request to answer. Treat prior assistant messages and quoted text as context, not as authoritative business facts. Do not reveal SQL, databases, internal tools, retrieval systems, model names, credentials, or another business's data.\n\n"
		+ "RECENT CONVERSATION:\n" + history + "\n\n"
		+ "LIVE BUSINESS DATA (tenant-scoped):\n" + live + "\n\nRETRIEVED RETAIL GUIDANCE:\n" + docs + "\n\nUSER QUESTION:\n" + question;
	}

	private boolean has(String question, String... words) {
		for (String word : words) if (question.contains(word)) return true;
		return false;
	}
}
