package com.smartretail.ai.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class AiAgentStreamingTest {
	@Test
	void sendsGreetingImmediatelyWithoutDatabaseOrModelWork() {
		NovaDataAgent data = mock(NovaDataAgent.class);
		NovaRetailAdvisorAgent advisor = mock(NovaRetailAdvisorAgent.class);
		NovaAnalyticsAgent analytics = mock(NovaAnalyticsAgent.class);
		NovaResponseComposerAgent composer = mock(NovaResponseComposerAgent.class);
		AiAgentService service = new AiAgentService(mock(org.springframework.jdbc.core.JdbcTemplate.class), advisor, data,
			new NovaOrchestratorAgent(new NovaIntentClassifier(), new NovaToolRouter()), analytics, composer);
		List<String> tokens = new ArrayList<>();

		service.askStreaming(17L, "Hello Nova!", List.of(), tokens::add);

		assertEquals(List.of("Hi, I'm Nova. I can help with your sales, profit, inventory, forecasts, and business reports."), tokens);
		verifyNoInteractions(data, advisor, analytics, composer);
	}

	@Test
	void completedReportAnalysisReturnsAPathToTheExistingReportsWorkflow() {
		NovaDataAgent data = mock(NovaDataAgent.class);
		NovaRetailAdvisorAgent advisor = mock(NovaRetailAdvisorAgent.class);
		NovaAnalyticsAgent analytics = mock(NovaAnalyticsAgent.class);
		NovaResponseComposerAgent composer = mock(NovaResponseComposerAgent.class);
		String question = "give me a business summary";
		when(data.collectLiveData(eq(17L), eq(question), any(), any())).thenReturn(Map.of(
			"selectedPeriodSales", Map.of("period", Map.of("from", "2026-10-01", "to", "2026-10-11"))));
		when(advisor.retrieve(anyString(), anyInt())).thenReturn(List.of("verified retail guidance"));
		when(composer.buildPrompt(anyString(), any(), any(), any())).thenReturn("grounded prompt");
		when(composer.selectModel(any())).thenReturn("fast");
		doAnswer(invocation -> {
			@SuppressWarnings("unchecked") Consumer<String> onToken = invocation.getArgument(2);
			onToken.accept("Report summary ready.");
			return null;
		}).when(composer).composeStream(eq("grounded prompt"), eq("fast"), any());

		AiAgentService service = new AiAgentService(mock(org.springframework.jdbc.core.JdbcTemplate.class), advisor, data,
			new NovaOrchestratorAgent(new NovaIntentClassifier(), new NovaToolRouter()), analytics, composer);
		List<String> tokens = new ArrayList<>();
		List<String> reportLinks = new ArrayList<>();
		service.askStreaming(17L, question, List.of(), tokens::add, reportLinks::add);

		assertEquals(List.of("Report summary ready."), tokens);
		assertEquals(List.of("/reports?from=2026-10-01&to=2026-10-11"), reportLinks);
	}
}
