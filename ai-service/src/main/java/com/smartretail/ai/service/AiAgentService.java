package com.smartretail.ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.math.BigDecimal;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.text.NumberFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AiAgentService {
	private static final Logger log = LoggerFactory.getLogger(AiAgentService.class);
	private final JdbcTemplate jdbc;
	private final NovaRetailAdvisorAgent retailAdvisor;
	private final NovaDataAgent dataAgent;
	private final NovaOrchestratorAgent orchestrator;
	private final NovaAnalyticsAgent analyticsAgent;
	private final NovaResponseComposerAgent responseComposer;
	private final NovaLossRecoveryPlanner lossRecoveryPlanner;

	@Autowired
	public AiAgentService(JdbcTemplate jdbc, NovaRetailAdvisorAgent retailAdvisor, NovaDataAgent dataAgent,
			NovaOrchestratorAgent orchestrator, NovaAnalyticsAgent analyticsAgent,
			NovaResponseComposerAgent responseComposer, NovaLossRecoveryPlanner lossRecoveryPlanner) {
		this.jdbc = jdbc;
		this.retailAdvisor = retailAdvisor;
		this.dataAgent = dataAgent;
		this.orchestrator = orchestrator;
		this.analyticsAgent = analyticsAgent;
		this.responseComposer = responseComposer;
		this.lossRecoveryPlanner = lossRecoveryPlanner;
	}

	/** Compatibility constructor for focused tests and direct construction. */
	public AiAgentService(JdbcTemplate jdbc, NovaRetailAdvisorAgent retailAdvisor, NovaDataAgent dataAgent,
			NovaOrchestratorAgent orchestrator, NovaAnalyticsAgent analyticsAgent,
			NovaResponseComposerAgent responseComposer) {
		this(jdbc, retailAdvisor, dataAgent, orchestrator, analyticsAgent, responseComposer, new NovaLossRecoveryPlanner());
	}

	/** Compatibility constructor for direct service construction in focused tests. */
	public AiAgentService(JdbcTemplate jdbc, KnowledgeRetriever knowledge, ForecastEngine forecastEngine,
			NovaIntentClassifier intentClassifier, NovaToolRouter toolRouter, NovaCalculationEngine calculationEngine,
			RestClient.Builder restClientBuilder, @Value("${ollama.base-url}") String ollamaUrl,
			@Value("${ollama.model}") String model, @Value("${ollama.timeout-seconds:30}") int timeoutSeconds) {
		this(jdbc, new NovaRetailAdvisorAgent(knowledge),
			new NovaDataAgent(jdbc, new NovaForecastAgent(forecastEngine)),
			new NovaOrchestratorAgent(intentClassifier, toolRouter), new NovaAnalyticsAgent(calculationEngine),
			new NovaResponseComposerAgent(restClientBuilder, ollamaUrl, model, timeoutSeconds), new NovaLossRecoveryPlanner());
	}

	public Map<String, Object> ask(Long businessId, String question) {
		return ask(businessId, question, List.of());
	}

	public Map<String, Object> ask(Long businessId, String question, List<ChatTurn> history) {
		long requestStarted = System.nanoTime();
		String query = question.trim();
		if (query.isEmpty() || query.length() > 2000) throw new IllegalArgumentException("Ask a question using 1 to 2000 characters.");
		List<ChatTurn> safeHistory = safeHistory(history);
		String quickReply = quickReply(query);
		if (quickReply != null) {
			log.info("nova.request path=quick_reply total_ms={}", elapsedMs(requestStarted));
			return response(quickReply);
		}
		long intentStarted = System.nanoTime();
		NovaOrchestratorAgent.Plan plan = orchestrator.plan(query, safeHistory);
		NovaIntentClassifier.Classification classification = plan.classification();
		log.info("nova.stage name=intent duration_ms={} tool_count={}", elapsedMs(intentStarted), plan.tools().size());
		if (classification.needsClarification()) { log.info("nova.request path=clarification total_ms={}", elapsedMs(requestStarted)); return response(clarificationAnswer(query)); }
		if (classification.todaySales()) { Map<String, Object> result = todaySalesResponse(businessId, classification.effectiveQuery()); log.info("nova.request path=today_sales total_ms={}", elapsedMs(requestStarted)); return result; }
		if (classification.intents().contains(NovaIntentClassifier.Intent.RETURNS)
			&& !classification.intents().contains(NovaIntentClassifier.Intent.INVOICES))
			{ log.info("nova.request path=returns_unavailable total_ms={}", elapsedMs(requestStarted)); return response(returnsUnavailableAnswer(query)); }
		if (isDeterministicProfitQuestion(query, classification)) {
			Map<String, Object> result = response(selectedPeriodProfitAnswer(businessId, query, classification, plan.tools()));
			log.info("nova.request path=period_profit total_ms={}", elapsedMs(requestStarted));
			return result;
		}
		if (classification.intents().equals(Set.of(NovaIntentClassifier.Intent.CALCULATION))) { log.info("nova.request path=calculation total_ms={}", elapsedMs(requestStarted)); return response(calculationAnswer(query)); }
		if (classification.intents().equals(Set.of(NovaIntentClassifier.Intent.SALES))) { Map<String, Object> result = response(selectedPeriodSalesAnswer(businessId, query, classification, plan.tools())); log.info("nova.request path=period_sales total_ms={}", elapsedMs(requestStarted)); return result; }
		String toolQuestion = classification.effectiveQuery();
		long dataStarted = System.nanoTime();
		Map<String, Object> live = dataAgent.collectLiveData(businessId, query, classification, plan.tools());
		if (classification.intents().contains(NovaIntentClassifier.Intent.LOSS_RECOVERY))
			live.put("lossRecoveryPlan", lossRecoveryPlanner.buildPlan(live, lossRecoveryPlanner.recoveryDays(query)));
		log.info("nova.stage name=business_data duration_ms={}", elapsedMs(dataStarted));
		List<String> retrieved = plan.tools().contains(NovaToolRouter.Tool.RETAIL_GUIDANCE) ? retailAdvisor.retrieve(toolQuestion, 3) : List.of();

		String prompt = responseComposer.buildPrompt(query, live, retrieved, safeHistory);
		String answer;
		long selectionStarted = System.nanoTime();
		String selectedModel = responseComposer.selectModel(classification);
		log.info("nova.stage name=model_selection duration_ms={}", elapsedMs(selectionStarted));
		long inferenceStarted = System.nanoTime();
		try { answer = responseComposer.compose(prompt, selectedModel); log.info("nova.stage name=model_inference duration_ms={}", elapsedMs(inferenceStarted)); }
		catch (RuntimeException error) {
			log.warn("Ollama request failed.");
			throw new IllegalStateException("Nova could not generate a response just now. Please try again shortly.", error);
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("answer", answer);
		result.put("agent", "Nova");
		log.info("nova.request path=model total_ms={}", elapsedMs(requestStarted));
		return result;
	}

	/** Runs the same tenant-scoped orchestration pipeline and streams composed answer tokens. */
	public void askStreaming(Long businessId, String question, List<ChatTurn> history, Consumer<String> onToken) {
		askStreaming(businessId, question, history, onToken, ignored -> { });
	}

	/** Optional report callback links completed Nova analysis to the existing authorized reports UI. */
	public void askStreaming(Long businessId, String question, List<ChatTurn> history, Consumer<String> onToken,
			Consumer<String> onReportLink) {
		long requestStarted = System.nanoTime();
		String query = question.trim();
		if (query.isEmpty() || query.length() > 2000) throw new IllegalArgumentException("Ask a question using 1 to 2000 characters.");
		List<ChatTurn> safeHistory = safeHistory(history);
		String quickReply = quickReply(query);
		if (quickReply != null) {
			onToken.accept(quickReply);
			log.info("nova.request path=quick_reply total_ms={}", elapsedMs(requestStarted));
			return;
		}
		long intentStarted = System.nanoTime();
		NovaOrchestratorAgent.Plan plan = orchestrator.plan(query, safeHistory);
		NovaIntentClassifier.Classification classification = plan.classification();
		log.info("nova.stage name=intent duration_ms={} tool_count={}", elapsedMs(intentStarted), plan.tools().size());
		if (classification.needsClarification()) {
			onToken.accept(clarificationAnswer(query));
			log.info("nova.request path=clarification total_ms={}", elapsedMs(requestStarted));
			return;
		}
		if (classification.todaySales()) {
			onToken.accept((String) todaySalesResponse(businessId, classification.effectiveQuery()).get("answer"));
			log.info("nova.request path=today_sales total_ms={}", elapsedMs(requestStarted));
			return;
		}
		if (classification.intents().contains(NovaIntentClassifier.Intent.RETURNS)
			&& !classification.intents().contains(NovaIntentClassifier.Intent.INVOICES)) {
			onToken.accept(returnsUnavailableAnswer(query));
			log.info("nova.request path=returns_unavailable total_ms={}", elapsedMs(requestStarted));
			return;
		}
		if (isDeterministicProfitQuestion(query, classification)) {
			onToken.accept(selectedPeriodProfitAnswer(businessId, query, classification, plan.tools()));
			log.info("nova.request path=period_profit total_ms={}", elapsedMs(requestStarted));
			return;
		}
		if (classification.intents().equals(Set.of(NovaIntentClassifier.Intent.CALCULATION))) {
			onToken.accept(calculationAnswer(query));
			log.info("nova.request path=calculation total_ms={}", elapsedMs(requestStarted));
			return;
		}
		if (classification.intents().equals(Set.of(NovaIntentClassifier.Intent.SALES))) {
			onToken.accept(selectedPeriodSalesAnswer(businessId, query, classification, plan.tools()));
			log.info("nova.request path=period_sales total_ms={}", elapsedMs(requestStarted));
			return;
		}
		long dataStarted = System.nanoTime();
		Map<String, Object> live = dataAgent.collectLiveData(businessId, query, classification, plan.tools());
		if (classification.intents().contains(NovaIntentClassifier.Intent.LOSS_RECOVERY))
			live.put("lossRecoveryPlan", lossRecoveryPlanner.buildPlan(live, lossRecoveryPlanner.recoveryDays(query)));
		log.info("nova.stage name=business_data duration_ms={}", elapsedMs(dataStarted));
		List<String> retrieved = plan.tools().contains(NovaToolRouter.Tool.RETAIL_GUIDANCE)
			? retailAdvisor.retrieve(classification.effectiveQuery(), 3) : List.of();
		String prompt = responseComposer.buildPrompt(query, live, retrieved, safeHistory);
		long selectionStarted = System.nanoTime();
		String selectedModel = responseComposer.selectModel(classification);
		log.info("nova.stage name=model_selection duration_ms={}", elapsedMs(selectionStarted));
		long inferenceStarted = System.nanoTime();
		java.util.concurrent.atomic.AtomicBoolean firstToken = new java.util.concurrent.atomic.AtomicBoolean(false);
		try {
			responseComposer.composeStream(prompt, selectedModel, token -> {
				if (firstToken.compareAndSet(false, true)) log.info("nova.stage name=time_to_first_token_ms duration_ms={}", elapsedMs(inferenceStarted));
				onToken.accept(token);
			});
			if (classification.intents().contains(NovaIntentClassifier.Intent.REPORT)) {
				@SuppressWarnings("unchecked")
				Map<String, Object> sales = (Map<String, Object>) live.get("selectedPeriodSales");
				@SuppressWarnings("unchecked")
				Map<String, Object> period = sales == null ? Map.of() : (Map<String, Object>) sales.get("period");
				LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
				String from = String.valueOf(period.getOrDefault("from", today.minusDays(29).toString()));
				String to = String.valueOf(period.getOrDefault("to", today.toString()));
				onReportLink.accept("/reports?from=" + from + "&to=" + to);
			}
			log.info("nova.stage name=model_inference duration_ms={}", elapsedMs(inferenceStarted));
			log.info("nova.request path=model total_ms={}", elapsedMs(requestStarted));
		} catch (RuntimeException error) {
			log.warn("Ollama streaming request failed.", error);
			throw new IllegalStateException("Nova could not generate a response just now. Please try again shortly.", error);
		}
	}

	private String selectedPeriodSalesAnswer(Long businessId, String query,
			NovaIntentClassifier.Classification classification, Set<NovaToolRouter.Tool> tools) {
		Map<String, Object> live = dataAgent.collectLiveData(businessId, query, classification, tools);
		@SuppressWarnings("unchecked")
		Map<String, Object> sales = (Map<String, Object>) live.get("selectedPeriodSales");
		@SuppressWarnings("unchecked")
		Map<String, Object> period = (Map<String, Object>) sales.get("period");
		@SuppressWarnings("unchecked")
		Map<String, Object> totals = (Map<String, Object>) sales.get("summary");
		NumberFormat inr = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
		inr.setMaximumFractionDigits(2);
		inr.setMinimumFractionDigits(0);
		long invoiceCount = number(totals.get("invoice_count")).longValue();
		long itemsSold = number(totals.get("items_sold")).longValue();
		StringBuilder answer = new StringBuilder("Recorded sales from ")
			.append(period.get("from")).append(" to ").append(period.get("to")).append(":\n")
			.append("- Total sales: INR ").append(inr.format(number(totals.get("total_sales")))).append("\n")
			.append("- Bills: ").append(invoiceCount).append("\n")
			.append("- Items sold: ").append(inr.format(itemsSold));
		if (invoiceCount == 0) answer.append("\nNo sales were recorded for this period.");
		return answer.toString();
	}

	private boolean isDeterministicProfitQuestion(String query, NovaIntentClassifier.Classification classification) {
		Set<NovaIntentClassifier.Intent> intents = classification.intents();
		if (intents.equals(Set.of(NovaIntentClassifier.Intent.PROFIT))) return true;
		return intents.equals(Set.of(NovaIntentClassifier.Intent.PROFIT, NovaIntentClassifier.Intent.CALCULATION))
			&& analyticsAgent.calculate(query).isEmpty();
	}

	private String selectedPeriodProfitAnswer(Long businessId, String query,
			NovaIntentClassifier.Classification classification, Set<NovaToolRouter.Tool> tools) {
		Map<String, Object> live = dataAgent.collectLiveData(businessId, query, classification, tools);
		@SuppressWarnings("unchecked")
		Map<String, Object> insights = (Map<String, Object>) live.get("profitInsights");
		@SuppressWarnings("unchecked")
		Map<String, Object> overall = (Map<String, Object>) insights.get("overall");
		@SuppressWarnings("unchecked")
		Map<String, Object> period = (Map<String, Object>) insights.get("period");
		BigDecimal revenue = decimal(overall.get("net_revenue"));
		BigDecimal cost = decimal(overall.get("cost_of_goods"));
		BigDecimal grossProfit = decimal(overall.get("gross_profit"));
		NumberFormat inr = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
		inr.setMinimumFractionDigits(2);
		inr.setMaximumFractionDigits(2);
		StringBuilder answer = new StringBuilder("Recorded profit/loss from ")
			.append(period.get("from")).append(" to ").append(period.get("to"))
			.append(" (inclusive, business dates):\n")
			.append("- Revenue (GST excluded where applicable): INR ").append(inr.format(revenue)).append("\n")
			.append("- Cost of goods sold: INR ").append(inr.format(cost)).append("\n");
		if (grossProfit.signum() < 0)
			answer.append("- Gross loss: INR ").append(inr.format(grossProfit.abs())).append("\n");
		else answer.append("- Gross profit: INR ").append(inr.format(grossProfit)).append("\n");
		if (revenue.signum() > 0) {
			BigDecimal margin = grossProfit.multiply(BigDecimal.valueOf(100))
				.divide(revenue, 2, java.math.RoundingMode.HALF_UP);
			answer.append("- Gross margin: ").append(margin.toPlainString()).append("%\n");
		}
		Object lowMarginValue = insights.get("lowestMarginProducts");
		if (lowMarginValue instanceof List<?> products && !products.isEmpty()) {
			List<String> reviewItems = new ArrayList<>();
			for (Object value : products) {
				if (value instanceof Map<?, ?> product) {
					Object name = product.get("product_name");
					Object productMargin = product.get("gross_margin_percent");
					if (name != null && productMargin != null)
						reviewItems.add(name + " (" + productMargin + "% gross margin)");
				}
				if (reviewItems.size() == 3) break;
			}
			if (!reviewItems.isEmpty()) answer.append("\nProducts to review: ").append(String.join(", ", reviewItems))
				.append(". Verify recorded costs and discounts before deciding on a price change.");
		}
		answer.append("Net profit/loss cannot be calculated because operating expenses are not recorded in the available business data.");
		return answer.toString();
	}

	private String quickReply(String question) {
		String normalized = question.toLowerCase(Locale.ROOT)
			.replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
		if (Set.of("hi", "hello", "hey", "hello nova", "hi nova", "hey nova", "good morning",
			"good afternoon", "good evening").contains(normalized))
			return "Hi, I'm Nova. I can help with your sales, profit, inventory, forecasts, and business reports.";
		if (Set.of("thanks", "thank you", "thanks nova", "thank you nova").contains(normalized))
			return "You're welcome. Ask me about your business whenever you're ready.";
		if (Set.of("what can you do", "what can nova do", "how can you help", "how can nova help").contains(normalized))
			return "I can analyze recorded sales and gross profit, review inventory risks, forecast demand, compare periods, and prepare business reports.";
		return null;
	}

	private BigDecimal decimal(Object value) {
		if (value instanceof BigDecimal decimal) return decimal;
		if (value instanceof Number number) return new BigDecimal(number.toString());
		return BigDecimal.ZERO;
	}

	private Number number(Object value) {
		return value instanceof Number number ? number : 0;
	}


	private Map<String, Object> todaySalesResponse(Long businessId, String question) {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
		Map<String, Object> totals = jdbc.queryForMap("""
			SELECT COUNT(i.id) AS total_bills,
			       COALESCE(SUM(i.total_amount), 0) AS total_sales,
			       COALESCE((SELECT SUM(ii.quantity)
			         FROM invoice_items ii JOIN invoices item_invoice ON item_invoice.id = ii.invoice_id
			         WHERE item_invoice.business_id = ? AND item_invoice.sold_at >= ? AND item_invoice.sold_at < ?), 0) AS items_sold
			FROM invoices i
			WHERE i.business_id = ? AND i.sold_at >= ? AND i.sold_at < ?
			""", businessId, today.atStartOfDay(), today.plusDays(1).atStartOfDay(), businessId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
		NumberFormat inr = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
		inr.setMaximumFractionDigits(2);
		inr.setMinimumFractionDigits(0);
		Number salesValue = (Number) totals.get("total_sales");
		String sales = inr.format(salesValue);
		boolean marathi = has(question, "विक्री", "आजची", "आहे", "सांगा", "माझ्या", "किती", "ajchi", "vikri", "majhi", "sanga");
		boolean hindi = !marathi && has(question, "बिक्री", "आज", "है", "बताओ", "मेरी", "कितनी", "aaj ki", "aaj ka", "bikri", "kitni", "kitna", "meri", "batao");
		Locale responseLocale = marathi ? Locale.forLanguageTag("mr-IN") : hindi ? Locale.forLanguageTag("hi-IN") : Locale.ENGLISH;
		String date = today.format(DateTimeFormatter.ofPattern("d MMMM uuuu", responseLocale));
		boolean asksForeignCurrency = has(question.toLowerCase(Locale.ROOT), "usd", "dollar", "dollars", "$");
		String currencyNote = asksForeignCurrency ? "Amounts are stored in INR; no exchange rate is available, so I have not converted them." : "";
		String answer;
		if (marathi) {
			answer = "आजची नोंदवलेली विक्री (" + date + "):\n💰 एकूण विक्री: ₹" + sales + "\n🧾 एकूण बिले: " + totals.get("total_bills")
				+ "\n📦 विकलेल्या वस्तू: " + inr.format((Number) totals.get("items_sold")) + "\n↩️ परतावा: नोंदी उपलब्ध नाहीत"
				+ "\n💵 निव्वळ विक्री: परताव्यांच्या नोंदी नसल्यामुळे मोजता येत नाही. परताव्यांपूर्वीची नोंदवलेली विक्री: ₹" + sales + "."
				+ (asksForeignCurrency ? "\nरक्कम INR मध्ये नोंदवली आहे; विनिमय दर उपलब्ध नसल्यामुळे रूपांतरण केलेले नाही." : "") + "\n\nआजचा नफा किंवा सर्वाधिक विकलेली उत्पादने पाहायची का?";
		} else if (hindi) {
			answer = "आज की दर्ज बिक्री (" + date + "):\n💰 कुल बिक्री: ₹" + sales + "\n🧾 कुल बिल: " + totals.get("total_bills")
				+ "\n📦 बेची गई वस्तुएँ: " + inr.format((Number) totals.get("items_sold")) + "\n↩️ रिटर्न: रिकॉर्ड उपलब्ध नहीं हैं"
				+ "\n💵 नेट बिक्री: रिटर्न दर्ज न होने के कारण गणना उपलब्ध नहीं है। रिटर्न से पहले दर्ज बिक्री: ₹" + sales + "."
				+ (asksForeignCurrency ? "\nराशि INR में दर्ज है; विनिमय दर उपलब्ध नहीं होने के कारण इसे बदला नहीं गया है।" : "") + "\n\nक्या आप आज का मुनाफ़ा या सबसे ज़्यादा बिकने वाले उत्पाद देखना चाहेंगे?";
		} else {
			answer = "Today's recorded sales so far (" + date + "):\n💰 Total Sales: ₹" + sales + "\n🧾 Total Bills: " + totals.get("total_bills")
				+ "\n📦 Items Sold: " + inr.format((Number) totals.get("items_sold")) + "\n↩️ Returns: Not tracked in the billing records"
				+ "\n💵 Net Sales: Cannot be calculated because returns are not tracked. Recorded sales before returns: ₹" + sales + "."
				+ (asksForeignCurrency ? "\n" + currencyNote : "") + "\n\nWould you like to see today's profit or top-selling products?";
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("answer", answer);
		result.put("agent", "Nova");
		return result;
	}

	private String clarificationAnswer(String question) {
		if (has(question, "आज", "बिक्री", "ग्राहक", "उत्पाद", "कौन सा")) return "कृपया बताएं कि आप किस उत्पाद, ग्राहक या तारीख के बारे में पूछ रहे हैं।";
		if (has(question, "विक्री", "ग्राहक", "उत्पादन", "कोणता")) return "कृपया कोणते उत्पादन, ग्राहक किंवा तारीख विचारत आहात ते सांगा.";
		return "Which product, customer, or date do you mean?";
	}

	private String returnsUnavailableAnswer(String question) {
		if (has(question, "विक्री", "आहे", "परतावा")) return "सध्याच्या बिलिंग नोंदींमध्ये परतावा नोंदवलेला नाही, त्यामुळे परताव्याची रक्कम सांगता येत नाही.";
		if (has(question, "बिक्री", "है", "वापसी", "रिटर्न")) return "बिलिंग रिकॉर्ड में अभी रिटर्न दर्ज नहीं होते, इसलिए वास्तविक रिटर्न राशि नहीं बता सकता।";
		return "The current billing records do not track returns or refunds, so I can't provide a real return total.";
	}

	private Map<String, Object> response(String answer) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("answer", answer);
		result.put("agent", "Nova");
		return result;
	}

	private String calculationAnswer(String question) {
		boolean rupees = has(question.toLowerCase(Locale.ROOT), "inr", "rupee", "rupees", "rs.", "₹");
		boolean dollars = has(question.toLowerCase(Locale.ROOT), "usd", "dollar", "dollars", "$");
		if (rupees && dollars) return "This expression mixes INR and dollars. I can't add or compare them without an exchange rate.";
		var calculated = analyticsAgent.calculate(question);
		if (calculated.isEmpty()) return "Please include a calculation, such as 25% of 800 or 120 + 35.";
		NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
		format.setMaximumFractionDigits(4);
		String amount = format.format(calculated.get());
		String symbol = rupees ? "₹" : dollars ? "$" : "";
		return "Result: " + symbol + amount;
	}

	private List<ChatTurn> safeHistory(List<ChatTurn> history) {
		if (history == null || history.isEmpty()) return List.of();
		return history.stream().filter(turn -> turn != null && turn.role() != null && turn.content() != null
			&& ("user".equals(turn.role()) || "assistant".equals(turn.role())))
			.skip(Math.max(0, history.size() - 8L))
			.map(turn -> new ChatTurn(turn.role(), turn.content().substring(0, Math.min(1000, turn.content().length()))))
			.toList();
	}

	public record ChatTurn(String role, String content) { }


	private boolean has(String question, String... words) { for (String word : words) if (question.contains(word)) return true; return false; }

	private long elapsedMs(long startedAt) { return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt); }
}
