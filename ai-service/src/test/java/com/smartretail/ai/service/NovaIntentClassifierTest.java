package com.smartretail.ai.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.smartretail.ai.service.NovaIntentClassifier.Intent;
import static com.smartretail.ai.service.NovaToolRouter.Tool;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NovaIntentClassifierTest {
	private final NovaIntentClassifier classifier = new NovaIntentClassifier();
	private final NovaToolRouter router = new NovaToolRouter();

	@Test
	void routesEnglishHindiMarathiAndCommonSalesTypos() {
		assertTrue(classifier.classify("show todays salse", List.of()).todaySales());
		assertTrue(classifier.classify("आज की बिक्री बताओ", List.of()).todaySales());
		assertTrue(classifier.classify("आजची विक्री किती आहे?", List.of()).todaySales());
		assertTrue(classifier.classify("आजची विक्री", List.of()).todaySales());
		assertTrue(classifier.classify("forecast next week's sales", List.of()).intents().containsAll(Set.of(Intent.FORECAST, Intent.SALES)));
	}

	@Test
	void routesInformalLastSevenDaysSalesQuestionToSalesOnly() {
		var classification = classifier.classify("my query is last 7 day sell", List.of());
		assertEquals(Set.of(Intent.SALES), classification.intents());
		assertEquals(Set.of(Tool.SALES_SUMMARY), router.route(classification));
	}

	@Test
	void choosesOnlyToolsRequiredForEachBusinessQuestion() {
		var stock = classifier.classify("how much stock should I keep?", List.of());
		assertEquals(Set.of(Tool.INVENTORY, Tool.PRODUCT_MATCHES, Tool.STOCK_ADVICE, Tool.RETAIL_GUIDANCE), router.route(stock));

		var profit = classifier.classify("show my gross profit", List.of());
		assertEquals(Set.of(Tool.PROFIT_INSIGHTS, Tool.SALES_SUMMARY, Tool.RETAIL_GUIDANCE), router.route(profit));
		assertFalse(router.route(profit).contains(Tool.SUPPLIER_ANALYSIS));
		assertEquals(Set.of(Tool.SALES_SUMMARY), router.route(classifier.classify("today's sales", List.of())));
		assertEquals(Set.of(Tool.SALES_SUMMARY, Tool.DAILY_SALES_HISTORY, Tool.FORECAST_ENGINE, Tool.RETAIL_GUIDANCE),
			router.route(classifier.classify("forecast sales for next week", List.of())));
		assertEquals(Set.of(Tool.CUSTOMER_ANALYSIS), router.route(classifier.classify("show top customers", List.of())));
		assertEquals(Set.of(Tool.SUPPLIER_ANALYSIS), router.route(classifier.classify("show suppliers", List.of())));
		assertEquals(Set.of(Tool.PRODUCT_COMBINATIONS), router.route(classifier.classify("which products are frequently bought together?", List.of())));
		assertEquals(Set.of(Tool.INVOICE_SEARCH), router.route(classifier.classify("open invoice INV-20261009", List.of())));
		assertEquals(Set.of(Intent.INVOICES), classifier.classify("open invoice INV-20261009", List.of()).intents());
		assertEquals(Set.of(Tool.SALES_SUMMARY, Tool.INVOICE_SEARCH), router.route(classifier.classify("show sales and invoice totals", List.of())));
		assertEquals(Set.of(Tool.TOP_PRODUCTS, Tool.SALES_SUMMARY), router.route(classifier.classify("show best selling products", List.of())));

		var report = classifier.classify("give me a business summary", List.of());
		assertTrue(router.route(report).containsAll(Set.of(Tool.BUSINESS_SNAPSHOT, Tool.SALES_SUMMARY, Tool.INVENTORY, Tool.CUSTOMER_ANALYSIS)));
	}

	@Test
	void usesConversationContextForShortFollowUpsAndClarifiesOnlyWithoutContext() {
		var followUp = classifier.classify("and today?", List.of(
			new AiAgentService.ChatTurn("user", "show sales"),
			new AiAgentService.ChatTurn("assistant", "Recorded sales are ready.")));
		assertTrue(followUp.todaySales());
		assertFalse(followUp.needsClarification());
		assertTrue(classifier.classify("what about it?", List.of()).needsClarification());
	}

	@Test
	void keepsGeneralQuestionsOffBusinessDataToolsAndPreservesCurrencyIntent() {
		var general = classifier.classify("How should a small shop reduce waste?", List.of());
		assertEquals(Set.of(Tool.RETAIL_GUIDANCE), router.route(general));
		assertTrue(classifier.classify("today sales in dollars", List.of()).asksForForeignCurrency());
		assertEquals(Set.of(Tool.CALCULATOR), router.route(classifier.classify("what is 25% of 800?", List.of())));
	}

	@Test
	void routesPeriodComparisonsSlowMovingProductsAndGrowthAnalysisToEvidenceTools() {
		var comparison = classifier.classify("compare this month with last month sales", List.of());
		assertTrue(comparison.intents().containsAll(Set.of(Intent.SALES, Intent.COMPARISON)));
		assertTrue(router.route(comparison).containsAll(Set.of(Tool.SALES_SUMMARY, Tool.PERIOD_COMPARISON)));

		assertTrue(router.route(classifier.classify("show slow-moving products", List.of()))
			.containsAll(Set.of(Tool.SLOW_MOVING_PRODUCTS, Tool.RETAIL_GUIDANCE)));
		assertTrue(router.route(classifier.classify("find growth opportunities for my business", List.of()))
			.containsAll(Set.of(Tool.BUSINESS_SNAPSHOT, Tool.INVENTORY, Tool.SLOW_MOVING_PRODUCTS,
				Tool.PROFIT_INSIGHTS, Tool.RETAIL_GUIDANCE)));
	}

	@Test
	void classifiesTheThreeBusinessGrowthQueriesAndChoosesFocusedDataTools() {
		var report = classifier.classify("generate a today report", List.of());
		assertTrue(report.intents().containsAll(Set.of(Intent.REPORT, Intent.TODAY_REPORT)));
		assertFalse(report.todaySales());
		assertEquals(Set.of(Tool.SALES_SUMMARY, Tool.PROFIT_INSIGHTS), router.route(report));

		var profitCalculation = classifier.classify("today business loss and profit calculate", List.of());
		assertTrue(profitCalculation.intents().containsAll(Set.of(Intent.PROFIT, Intent.CALCULATION)));
		assertFalse(profitCalculation.todaySales());

		var recovery = classifier.classify("how to cover today's loss within 7 days", List.of());
		assertTrue(recovery.intents().containsAll(Set.of(Intent.PROFIT, Intent.LOSS_RECOVERY, Intent.GROWTH_ANALYSIS)));
		assertTrue(router.route(recovery).containsAll(Set.of(Tool.PROFIT_INSIGHTS, Tool.LOSS_RECOVERY_BASELINE,
			Tool.SLOW_MOVING_PRODUCTS, Tool.RETAIL_GUIDANCE)));
		assertFalse(router.route(recovery).contains(Tool.BUSINESS_SNAPSHOT));
	}

	@Test
	void classifiesRetentionAcquisitionAndCostReductionSeparately() {
		assertTrue(classifier.classify("how can I improve customer retention?", List.of()).intents().contains(Intent.CUSTOMER_RETENTION));
		assertTrue(classifier.classify("how can I attract new customers?", List.of()).intents().contains(Intent.CUSTOMER_ACQUISITION));
		assertTrue(classifier.classify("how can I reduce operating costs?", List.of()).intents().contains(Intent.COST_REDUCTION));
	}
}
