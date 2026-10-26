package com.smartretail.ai.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/** Handles explicit deterministic arithmetic without evaluating arbitrary code. */
@Component
public class NovaAnalyticsAgent {
	private final NovaCalculationEngine engine;

	public NovaAnalyticsAgent(NovaCalculationEngine engine) { this.engine = engine; }

	public Optional<BigDecimal> calculate(String question) { return engine.calculate(question); }
}
