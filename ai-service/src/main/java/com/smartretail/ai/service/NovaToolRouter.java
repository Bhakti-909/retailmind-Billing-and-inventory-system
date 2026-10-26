package com.smartretail.ai.service;

import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

import static com.smartretail.ai.service.NovaIntentClassifier.Intent;

/** Converts classified intent into the smallest allowlisted set of business tools. */
@Component
public class NovaToolRouter {
	public enum Tool {
		BUSINESS_SNAPSHOT, INVENTORY, STOCK_ADVICE, SALES_SUMMARY, DAILY_SALES_HISTORY,
		FORECAST_ENGINE, PROFIT_INSIGHTS, TOP_PRODUCTS, PRODUCT_MATCHES, CUSTOMER_ANALYSIS,
		INVOICE_SEARCH, SUPPLIER_ANALYSIS, PRODUCT_COMBINATIONS, RETAIL_GUIDANCE, CALCULATOR,
		PERIOD_COMPARISON, SLOW_MOVING_PRODUCTS, LOSS_RECOVERY_BASELINE
	}

	public Set<Tool> route(NovaIntentClassifier.Classification classification) {
		Set<Intent> intents = classification.intents();
		EnumSet<Tool> tools = EnumSet.noneOf(Tool.class);
		if (intents.contains(Intent.KNOWLEDGE)) tools.add(Tool.RETAIL_GUIDANCE);
		if (intents.contains(Intent.REPORT) && !intents.contains(Intent.TODAY_REPORT)) {
			tools.addAll(EnumSet.of(Tool.BUSINESS_SNAPSHOT, Tool.INVENTORY, Tool.SALES_SUMMARY,
				Tool.DAILY_SALES_HISTORY, Tool.TOP_PRODUCTS, Tool.CUSTOMER_ANALYSIS, Tool.SUPPLIER_ANALYSIS,
				Tool.STOCK_ADVICE, Tool.PROFIT_INSIGHTS, Tool.RETAIL_GUIDANCE));
		}
		if (intents.contains(Intent.TODAY_REPORT)) tools.addAll(EnumSet.of(
			Tool.SALES_SUMMARY, Tool.PROFIT_INSIGHTS));
		if (intents.contains(Intent.SALES)) tools.add(Tool.SALES_SUMMARY);
		if (intents.contains(Intent.FORECAST)) tools.addAll(EnumSet.of(Tool.SALES_SUMMARY, Tool.DAILY_SALES_HISTORY, Tool.FORECAST_ENGINE, Tool.RETAIL_GUIDANCE));
		if (intents.contains(Intent.INVENTORY)) tools.addAll(EnumSet.of(Tool.INVENTORY, Tool.PRODUCT_MATCHES, Tool.RETAIL_GUIDANCE));
		if (intents.contains(Intent.STOCK_ADVICE)) tools.addAll(EnumSet.of(Tool.INVENTORY, Tool.PRODUCT_MATCHES, Tool.STOCK_ADVICE, Tool.RETAIL_GUIDANCE));
		if (intents.contains(Intent.PROFIT)) tools.addAll(EnumSet.of(Tool.PROFIT_INSIGHTS, Tool.SALES_SUMMARY, Tool.RETAIL_GUIDANCE));
		if (intents.contains(Intent.CUSTOMERS)) tools.add(Tool.CUSTOMER_ANALYSIS);
		if (intents.contains(Intent.INVOICES)) tools.add(Tool.INVOICE_SEARCH);
		if (intents.contains(Intent.SUPPLIERS)) tools.add(Tool.SUPPLIER_ANALYSIS);
		if (intents.contains(Intent.PRODUCT_COMBINATIONS)) tools.add(Tool.PRODUCT_COMBINATIONS);
		if (intents.contains(Intent.TOP_PRODUCTS)) tools.addAll(EnumSet.of(Tool.TOP_PRODUCTS, Tool.SALES_SUMMARY));
		if (intents.contains(Intent.COMPARISON)) tools.addAll(EnumSet.of(Tool.PERIOD_COMPARISON, Tool.SALES_SUMMARY, Tool.RETAIL_GUIDANCE));
		if (intents.contains(Intent.SLOW_MOVING)) tools.addAll(EnumSet.of(Tool.SLOW_MOVING_PRODUCTS, Tool.RETAIL_GUIDANCE));
		boolean focusedGrowth = intents.contains(Intent.LOSS_RECOVERY) || intents.contains(Intent.CUSTOMER_RETENTION)
			|| intents.contains(Intent.CUSTOMER_ACQUISITION) || intents.contains(Intent.COST_REDUCTION);
		if (intents.contains(Intent.GROWTH_ANALYSIS) && !focusedGrowth) tools.addAll(EnumSet.of(Tool.BUSINESS_SNAPSHOT, Tool.INVENTORY, Tool.SALES_SUMMARY,
			Tool.DAILY_SALES_HISTORY, Tool.TOP_PRODUCTS, Tool.PROFIT_INSIGHTS, Tool.SLOW_MOVING_PRODUCTS,
			Tool.CUSTOMER_ANALYSIS, Tool.RETAIL_GUIDANCE));
		if (intents.contains(Intent.LOSS_RECOVERY)) tools.addAll(EnumSet.of(Tool.PROFIT_INSIGHTS,
			Tool.LOSS_RECOVERY_BASELINE, Tool.SLOW_MOVING_PRODUCTS, Tool.RETAIL_GUIDANCE));
		if (intents.contains(Intent.CUSTOMER_RETENTION) || intents.contains(Intent.CUSTOMER_ACQUISITION))
			tools.addAll(EnumSet.of(Tool.CUSTOMER_ANALYSIS, Tool.SALES_SUMMARY, Tool.RETAIL_GUIDANCE));
		if (intents.contains(Intent.COST_REDUCTION)) tools.addAll(EnumSet.of(Tool.INVENTORY,
			Tool.PROFIT_INSIGHTS, Tool.SLOW_MOVING_PRODUCTS, Tool.RETAIL_GUIDANCE));
		if (intents.equals(Set.of(Intent.CALCULATION))) tools.add(Tool.CALCULATOR);
		return Set.copyOf(tools);
	}
}
