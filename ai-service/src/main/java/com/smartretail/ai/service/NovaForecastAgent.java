package com.smartretail.ai.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Produces demand and sales trend estimates from the recent daily sales series. */
@Component
public class NovaForecastAgent {
	private final ForecastEngine engine;

	public NovaForecastAgent(ForecastEngine engine) { this.engine = engine; }

	public Map<String, Object> forecast(List<Map<String, Object>> dailySales) {
		return engine.forecast(dailySales);
	}
}
