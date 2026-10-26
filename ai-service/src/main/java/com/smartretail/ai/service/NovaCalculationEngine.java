package com.smartretail.ai.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Safe, deterministic calculator for explicit arithmetic questions; it never evaluates code. */
@Component
public class NovaCalculationEngine {
	private static final Pattern PERCENT_OF = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(?:%|percent|per cent)\\s*(?:of|का|चा)?\\s*(?:₹|\\$|INR\\s*|Rs\\.?\\s*)?(\\d+(?:\\.\\d+)?)");
	private static final Pattern EXPRESSION = Pattern.compile("(?i)((?<![\\d.])[+-]?(?:\\d+(?:\\.\\d+)?|\\()(?:(?:\\d+(?:\\.\\d+)?|[()+*/x×÷\\-\\s])*))");
	private static final Pattern WORD_EXPRESSION = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s+(plus|minus|times|multiplied by|divided by)\\s+(\\d+(?:\\.\\d+)?)");

	public Optional<BigDecimal> calculate(String question) {
		if (question == null) return Optional.empty();
		String normalizedNumbers = question.replaceAll("(?<=\\d),(?=\\d)", "");
		Matcher percent = PERCENT_OF.matcher(normalizedNumbers);
		if (percent.find()) return Optional.of(normalize(new BigDecimal(percent.group(1)).multiply(new BigDecimal(percent.group(2)))
			.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)));
		Matcher words = WORD_EXPRESSION.matcher(normalizedNumbers);
		if (words.find()) return Optional.of(normalize(operate(new BigDecimal(words.group(1)), words.group(2), new BigDecimal(words.group(3)))));
		Matcher expression = EXPRESSION.matcher(normalizedNumbers);
		if (!expression.find()) return Optional.empty();
		String value = expression.group(1).replace('×', '*').replace('x', '*').replace('X', '*').replace('÷', '/').replaceAll("\\s+", "");
		return evaluateExpression(value);
	}

	private BigDecimal normalize(BigDecimal value) {
		BigDecimal normalized = value.stripTrailingZeros();
		return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
	}

	private Optional<BigDecimal> evaluateExpression(String expression) {
		try {
			ArithmeticParser parser = new ArithmeticParser(expression);
			BigDecimal value = parser.expression();
			return parser.complete() ? Optional.of(normalize(value)) : Optional.empty();
		} catch (ArithmeticException | IllegalArgumentException error) {
			return Optional.empty();
		}
	}

	private BigDecimal operate(BigDecimal left, String operation, BigDecimal right) {
		return switch (operation.toLowerCase()) {
			case "plus" -> left.add(right);
			case "minus" -> left.subtract(right);
			case "times", "multiplied by" -> left.multiply(right);
			case "divided by" -> left.divide(right, 4, RoundingMode.HALF_UP);
			default -> throw new IllegalArgumentException("Unsupported arithmetic operation.");
		};
	}

	private static final class ArithmeticParser {
		private final String input;
		private int index;
		private ArithmeticParser(String input) { this.input = input; }

		private BigDecimal expression() {
			BigDecimal value = term();
			while (true) {
				skipSpaces();
				if (take('+')) value = value.add(term());
				else if (take('-')) value = value.subtract(term());
				else return value;
			}
		}

		private BigDecimal term() {
			BigDecimal value = factor();
			while (true) {
				skipSpaces();
				if (take('*')) value = value.multiply(factor());
				else if (take('/')) value = value.divide(factor(), 4, RoundingMode.HALF_UP);
				else return value;
			}
		}

		private BigDecimal factor() {
			skipSpaces();
			if (take('+')) return factor();
			if (take('-')) return factor().negate();
			if (take('(')) {
				BigDecimal value = expression();
				skipSpaces();
				if (!take(')')) throw new IllegalArgumentException("Missing closing parenthesis.");
				return value;
			}
			int start = index;
			while (index < input.length() && (Character.isDigit(input.charAt(index)) || input.charAt(index) == '.')) index++;
			if (start == index) throw new IllegalArgumentException("Expected a number.");
			return new BigDecimal(input.substring(start, index));
		}

		private boolean complete() { skipSpaces(); return index == input.length(); }
		private boolean take(char expected) { if (index < input.length() && input.charAt(index) == expected) { index++; return true; } return false; }
		private void skipSpaces() { while (index < input.length() && Character.isWhitespace(input.charAt(index))) index++; }
	}
}
