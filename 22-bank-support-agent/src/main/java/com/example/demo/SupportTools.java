package com.example.demo;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

/**
 * The support agent's tools. The {@link ToolContext} parameter is the analog of Pydantic
 * AI's {@code RunContext[SupportDependencies]}: it is invisible to the model and carries
 * the per-run dependencies (customer id + database connection) into the tool.
 */
public class SupportTools {

	@Tool(description = "Returns the customer's current account balance.")
	public String customerBalance(ToolContext toolContext) {
		SupportDependencies deps = dependencies(toolContext);
		return String.format("$%.2f", deps.db().customerBalance(deps.customerId()));
	}

	@Tool(description = "Returns the status of the customer's pending refunds.")
	public String refundStatus(ToolContext toolContext) {
		SupportDependencies deps = dependencies(toolContext);
		return deps.db().refundStatus(deps.customerId());
	}

	private static SupportDependencies dependencies(ToolContext toolContext) {
		return (SupportDependencies) toolContext.getContext().get(SupportDependencies.TOOL_CONTEXT_KEY);
	}

}
