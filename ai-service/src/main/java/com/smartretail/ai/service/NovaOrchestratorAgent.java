package com.smartretail.ai.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** Understands the request and selects the smallest allowlisted tool set. */
@Component
public class NovaOrchestratorAgent {
	private final NovaIntentClassifier classifier;
	private final NovaToolRouter router;

	public NovaOrchestratorAgent(NovaIntentClassifier classifier, NovaToolRouter router) {
		this.classifier = classifier;
		this.router = router;
	}

	public Plan plan(String question, List<AiAgentService.ChatTurn> history) {
		NovaIntentClassifier.Classification classification = classifier.classify(question, history);
		return new Plan(classification, router.route(classification));
	}

	public record Plan(NovaIntentClassifier.Classification classification, Set<NovaToolRouter.Tool> tools) { }
}
