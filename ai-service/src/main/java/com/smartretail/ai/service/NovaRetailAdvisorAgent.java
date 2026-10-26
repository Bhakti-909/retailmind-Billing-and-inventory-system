package com.smartretail.ai.service;

import org.springframework.stereotype.Component;

import java.util.List;

/** Retrieves the small set of retail guidance passages relevant to a question. */
@Component
public class NovaRetailAdvisorAgent {
	private final KnowledgeRetriever knowledge;

	public NovaRetailAdvisorAgent(KnowledgeRetriever knowledge) { this.knowledge = knowledge; }

	public List<String> retrieve(String question, int limit) { return knowledge.retrieve(question, limit); }
}
