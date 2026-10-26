package com.smartretail.ai.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NovaCalculationEngineTest {
	private final NovaCalculationEngine engine = new NovaCalculationEngine();

	@Test
	void calculatesExpressionsWithOperatorPrecedenceAndGrouping() {
		assertEquals(new BigDecimal("14"), engine.calculate("What is 2 + 3 * 4?").orElseThrow());
		assertEquals(new BigDecimal("20"), engine.calculate("calculate (2 + 3) * 4").orElseThrow());
		assertEquals(new BigDecimal("125"), engine.calculate("what is 25% of ₹500?").orElseThrow());
		assertEquals(new BigDecimal("14"), engine.calculate("What is 2 + 3 * 4?").orElseThrow());
		assertEquals(new BigDecimal("30"), engine.calculate("12,000 divided by 400").orElseThrow());
	}

	@Test
	void rejectsMalformedAndDivideByZeroExpressions() {
		assertTrue(engine.calculate("4 / 0").isEmpty());
		assertTrue(engine.calculate("calculate 25").isPresent());
		assertTrue(engine.calculate("hello there").isEmpty());
	}
}
