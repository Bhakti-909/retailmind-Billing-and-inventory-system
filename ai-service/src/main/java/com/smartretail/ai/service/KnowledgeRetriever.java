package com.smartretail.ai.service;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class KnowledgeRetriever {
	private final List<String> articles = List.of(
		"Reorder planning: compare on-hand quantity with the product reorder level. Prioritize out-of-stock items, then items with the largest shortfall. Review recent sales before ordering and account for supplier lead time.",
		"Retail pricing: gross margin is (selling price minus unit cost) divided by selling price. A selling price below cost creates a loss before overhead. Check taxes, product discounts, and competitor pricing before changing a price.",
		"Expiry management: prioritize expired stock for removal, then stock expiring within 30 days. Use first-expiry-first-out handling and review quantities before discounting.",
		"Sales forecasting: use recent daily sales as a baseline, compare the latest week with the prior week, and account for weekday patterns and known seasonal events. A short history is uncertain and should not be treated as a guarantee.",
		"Business reporting: invoice totals represent recorded bill totals. Gross profit uses recorded revenue less recorded product cost and does not include operating expenses, rent, wages, or payment fees.");

	public List<String> retrieve(String question, int limit) {
		String q = question.toLowerCase(Locale.ROOT);
		return articles.stream().map(article -> new Scored(article, score(q, article)))
			.filter(item -> item.score() > 0).sorted(Comparator.comparingInt(Scored::score).reversed())
			.limit(limit).map(Scored::text).toList();
	}

	private int score(String question, String article) {
		int score = 0;
		for (String word : article.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
			if (word.length() > 3 && question.contains(word)) score++;
		}
		return score;
	}
	private record Scored(String text, int score) { }
}
