package com.smartretail.ai.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Classifies a query before any business-data access is selected. */
@Component
public class NovaIntentClassifier {
	public enum Intent {
		SALES, FORECAST, INVENTORY, STOCK_ADVICE, PROFIT, CUSTOMERS, INVOICES,
		SUPPLIERS, PRODUCT_COMBINATIONS, TOP_PRODUCTS, RETURNS, REPORT, KNOWLEDGE,
		CLARIFICATION, CALCULATION, COMPARISON, SLOW_MOVING, GROWTH_ANALYSIS,
		TODAY_REPORT, LOSS_RECOVERY, CUSTOMER_RETENTION, CUSTOMER_ACQUISITION, COST_REDUCTION
	}

	public record Classification(Set<Intent> intents, String effectiveQuery, boolean todaySales,
		boolean asksForForeignCurrency, boolean needsClarification) { }

	private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+", Pattern.UNICODE_CHARACTER_CLASS);
	private static final Pattern ARITHMETIC_QUERY = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?\\s*(?:[+*/×÷]|-(?!\\s*\\d{4}))\\s*\\d+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?\\s*(?:%|percent|per cent)\\s*(?:of|का|चा)\\s*(?:₹|\\$|INR\\s*|Rs\\.?\\s*)?\\d+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?\\s+(?:plus|minus|times|multiplied by|divided by)\\s+\\d+(?:\\.\\d+)?)");

	public Classification classify(String question, List<AiAgentService.ChatTurn> history) {
		String rawCurrent = question == null ? "" : question.trim();
		String current = normalize(rawCurrent);
		String previousUserRaw = history == null ? "" : history.stream()
			.filter(turn -> turn != null && "user".equals(turn.role()) && turn.content() != null)
			.reduce((first, second) -> second).map(AiAgentService.ChatTurn::content).orElse("");
		String previousUser = normalize(previousUserRaw);
		boolean followUp = isFollowUp(current);
		boolean hasNewTopic = hasAny(current,
			"stock", "inventory", "product", "price", "sales", "sale", "sell", "sold", "revenue", "bill", "invoice",
			"customer", "profit", "margin", "forecast", "predict", "supplier", "vendor", "report", "return", "refund", "top selling",
			"pricing", "quantity", "expiry", "reorder", "restock", "top products", "best products",
			"विक्री", "बिक्री", "नफा", "मुनाफा", "ग्राहक", "साठा", "उत्पाद", "आपूर्तिकर्ता");
		String effective = followUp && !previousUser.isBlank() ? previousUserRaw + " " + rawCurrent : rawCurrent;
		String normalized = followUp && hasNewTopic ? current : normalize(effective);
		List<String> tokens = tokens(normalized);
		EnumSet<Intent> intents = EnumSet.noneOf(Intent.class);
		boolean invoiceLookup = hasAny(normalized, "open invoice", "find invoice", "search invoice", "look up invoice", "invoice details", "open bill", "find bill");

		boolean sales = !invoiceLookup && matches(normalized, tokens,
			"sales", "sale", "sell", "selling", "sold", "revenue", "turnover", "billing", "bill", "bills", "invoice", "invoices", "receipt", "receipts", "collection", "collected",
			"earn", "earned", "earnings", "income", "made today", "made yesterday", "how much did i make", "did i make", "did we make", "cash collected", "daily sales", "sales history", "today's sale",
			"bikri", "vikri", "becha", "विक्री", "बिक्री", "विक्रय", "सेल", "कमाई");
		boolean forecast = matches(normalized, tokens,
			"forecast", "forecasting", "predict", "predicting", "prediction", "expected", "expectation", "estimate", "projection", "future", "demand",
			"next week", "next month", "seasonality", "trend", "अंदाज", "अनुमान", "भविष्य", "उम्मीद");
		boolean inventory = matches(normalized, tokens,
			"inventory", "stock", "product", "products", "item", "items", "quantity", "available", "price", "pricing", "expiry", "expire",
			"reorder", "restock", "sku", "barcode", "stok", "inventry", "maal", "साठा", "स्टॉक", "माल", "उत्पाद", "सामान", "भंडार");
		boolean profit = matches(normalized, tokens,
			"profit", "profits", "margin", "margins", "earning", "earnings", "profitable", "cost", "loss", "net profit", "gross profit",
			"improve profit", "increase profit", "नफा", "मुनाफा", "लाभ", "कमाई");
		boolean customers = matches(normalized, tokens,
			"customer", "customers", "client", "buyer", "buyers", "loyalty", "repeat purchase", "top spender", "who bought",
			"ग्राहक", "ग्राहकों", "खरीदार");
		boolean invoices = matches(normalized, tokens,
			"invoice", "bill", "receipt", "transaction", "gst", "tax", "discount", "inv", "बिल", "रसीद");
		boolean returns = matches(normalized, tokens, "return", "returns", "refund", "refunds", "refunded", "रिटर्न", "वापसी", "परतावा");
		boolean suppliers = matches(normalized, tokens,
			"supplier", "suppliers", "vendor", "vendors", "purchase source", "आपूर्तिकर्ता", "विक्रेता");
		boolean combinations = matches(normalized, tokens,
			"combination", "bundle", "together", "frequently bought", "often bought", "साथ में");
		boolean topProducts = matches(normalized, tokens,
			"top selling", "best selling", "top products", "best products", "popular products", "top sellers", "best sellers");
		boolean specificInventory = matches(normalized, tokens,
			"stock", "inventory", "quantity", "price", "pricing", "expiry", "reorder", "restock", "sku", "barcode", "product price", "stock of");
		if ((combinations || topProducts || sales || profit) && !specificInventory) inventory = false;
		boolean calculation = ARITHMETIC_QUERY.matcher(rawCurrent).find()
			|| matches(normalized, tokens, "calculate", "compute", "calculation", "गणना", "हिशोब");
		boolean report = matches(normalized, tokens,
			"report", "reports", "overview", "summary", "summarize", "performance", "analysis", "analytics",
			"analyze my business", "analyse my business", "analyze my shop", "analyse my shop", "business summary", "shop summary", "store summary", "shop performance", "store performance", "business health", "business overview",
			"व्यवसायाचा सारांश", "व्यापार का सारांश");
		boolean comparison = hasAny(normalized, "compare", "comparison", "versus", "vs", "month over month", "month-over-month", "week over week", "week-over-week", "compared with", "compared to");
		boolean slowMoving = hasAny(normalized, "slow moving", "slow-moving", "slow selling", "slow-selling", "not selling", "no sales", "slow products");
		boolean todayReport = hasAny(normalized, "today", "today's", "todays", "aaj", "आज") && report;
		boolean lossRecovery = hasAny(normalized, "loss recovery", "recover loss", "recover the loss", "cover loss", "cover the loss",
			"make up for the loss", "offset the loss", "break even", "break-even")
			|| (hasAny(normalized, "loss") && hasAny(normalized, "recover", "cover", "offset", "make up", "earn back")
				&& hasAny(normalized, "within 7 days", "in 7 days", "next 7 days", "within a week", "in a week", "over 7 days"));
		boolean customerRetention = hasAny(normalized, "customer retention", "retain customers", "repeat customers", "returning customers",
			"bring customers back", "win back customers", "loyalty program", "repeat purchases");
		boolean customerAcquisition = hasAny(normalized, "customer acquisition", "acquire customers", "attract customers", "get new customers", "find new customers", "new customers");
		boolean costReduction = hasAny(normalized, "reduce costs", "reduce cost", "cut costs", "cut cost", "lower costs", "lower cost",
			"reduce expenses", "cost reduction", "save costs", "save money")
			|| (hasAny(normalized, "reduce", "cut", "lower", "trim") && hasAny(normalized, "cost", "costs", "expense", "expenses", "spending", "overhead"));
		boolean growthAnalysis = hasAny(normalized, "growth opportunities", "growth opportunity", "business growth", "grow my business", "how to grow", "improve sales", "increase sales", "growth ideas",
			"improve profit", "increase profit", "improve margins", "increase margin", "maximize profit", "boost profit", "grow sales", "sell more")
			|| lossRecovery || customerRetention || customerAcquisition || costReduction;

		if (sales) intents.add(Intent.SALES);
		if (forecast) intents.add(Intent.FORECAST);
		if (inventory) intents.add(Intent.INVENTORY);
		if (inventory && matches(normalized, tokens, "how much stock", "stock advisor", "reorder quantity", "how much should i keep", "restock", "कितना स्टॉक", "कितना माल", "कितना साठा")) intents.add(Intent.STOCK_ADVICE);
		if (profit) intents.add(Intent.PROFIT);
		if (customers) intents.add(Intent.CUSTOMERS);
		if (invoices) intents.add(Intent.INVOICES);
		if (returns) intents.add(Intent.RETURNS);
		if (suppliers) intents.add(Intent.SUPPLIERS);
		if (combinations) intents.add(Intent.PRODUCT_COMBINATIONS);
		if (topProducts) intents.add(Intent.TOP_PRODUCTS);
		if (report) intents.add(Intent.REPORT);
		if (calculation) intents.add(Intent.CALCULATION);
		if (comparison) intents.add(Intent.COMPARISON);
		if (slowMoving) intents.add(Intent.SLOW_MOVING);
		if (growthAnalysis) intents.add(Intent.GROWTH_ANALYSIS);
		if (todayReport) intents.add(Intent.TODAY_REPORT);
		if (lossRecovery) intents.add(Intent.LOSS_RECOVERY);
		if (customerRetention) intents.add(Intent.CUSTOMER_RETENTION);
		if (customerAcquisition) intents.add(Intent.CUSTOMER_ACQUISITION);
		if (costReduction) intents.add(Intent.COST_REDUCTION);

		boolean todaySales = hasAny(normalized, "today", "today's", "todays", "tody", "2day", "आज", "आजची", "आजचा", "आजचे", "aaj", "ajcha", "aajchi")
			&& sales && !forecast && !topProducts && !customers && !inventory && !invoices && !report && !profit && !calculation;
		boolean asksForForeignCurrency = hasAny(question == null ? "" : question.toLowerCase(Locale.ROOT), "usd", "dollar", "dollars", "$");
		boolean vagueReference = hasAny(current, "it", "that", "those", "them", "this one", "that one", "और", "उसका", "उसकी", "आणि", "त्याचा");
		boolean missingReferent = vagueReference || hasAny(current, "how many", "how much", "which one", "आज", "today", "कितना", "कितनी", "किती", "कोणता", "कोणती");
		boolean needsClarification = intents.isEmpty() && missingReferent && previousUser.isBlank();
		if (intents.isEmpty() && !needsClarification) intents.add(Intent.KNOWLEDGE);
		return new Classification(Set.copyOf(intents), effective, todaySales, asksForForeignCurrency, needsClarification);
	}

	private boolean isFollowUp(String text) {
		return hasWholeWord(text, "and", "also", "that", "those", "it", "them", "more", "why", "yes", "ok", "okay", "today", "aaj", "और", "उसका", "उसकी", "इसके", "वो", "हाँ", "आणि", "त्याचा", "त्याची", "होय")
			|| hasAny(text, "what about", "how about", "which one", "how many", "how much", "आज");
	}

	private boolean matches(String text, List<String> tokens, String... phrases) {
		if (hasAny(text, phrases)) return true;
		for (String token : tokens) {
			if (token.length() < 4 || !token.matches("[a-z]+")) continue;
			for (String phrase : phrases) {
				if (!phrase.matches("[a-z]+") || phrase.length() < 4) continue;
				if (distanceAtMostOne(token, phrase)) return true;
			}
		}
		return false;
	}

	private boolean distanceAtMostOne(String left, String right) {
		if (left.equals(right)) return true;
		if (Math.abs(left.length() - right.length()) > 1 || Math.min(left.length(), right.length()) < 4) return false;
		int i = 0, j = 0, edits = 0;
		while (i < left.length() && j < right.length()) {
			if (left.charAt(i) == right.charAt(j)) { i++; j++; continue; }
			if (++edits > 1) return false;
			if (i + 1 < left.length() && j + 1 < right.length()
				&& left.charAt(i) == right.charAt(j + 1) && left.charAt(i + 1) == right.charAt(j)) { i += 2; j += 2; }
			else if (left.length() > right.length()) i++;
			else if (right.length() > left.length()) j++;
			else { i++; j++; }
		}
		if (i < left.length() || j < right.length()) edits++;
		return edits <= 1;
	}

	private boolean hasWholeWord(String text, String... words) {
		for (String word : words) {
			String regex = "(?<![\\p{L}\\p{N}])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}])";
			if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).find()) return true;
		}
		return false;
	}

	private boolean hasAny(String text, String... phrases) {
		for (String phrase : phrases) {
			String normalizedPhrase = normalize(phrase);
			String regex = "(?<![\\p{L}\\p{N}])" + Pattern.quote(normalizedPhrase) + "(?![\\p{L}\\p{N}])";
			if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).find()) return true;
		}
		return false;
	}

	private List<String> tokens(String text) {
		List<String> result = new ArrayList<>();
		for (String token : TOKEN_SPLIT.split(text)) if (!token.isBlank()) result.add(token);
		return result;
	}

	private String normalize(String text) {
		return TOKEN_SPLIT.matcher(Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
			.toLowerCase(Locale.ROOT).trim()).replaceAll(" ").replaceAll("\\s+", " ").trim();
	}
}
