package com.example.demo.agent;

import java.util.List;

import com.example.demo.DatabaseConn;
import com.example.demo.KnowledgeBase;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

@Component
public class BankMcpTools {

	private final DatabaseConn db;

	private final VectorStore knowledgeBase;

	public BankMcpTools(DatabaseConn db, VectorStore knowledgeBase) {
		this.db = db;
		this.knowledgeBase = knowledgeBase;
	}

	@McpTool(description = "Get the customer's current account balance in USD.",
			annotations = @McpAnnotations(readOnlyHint = true, openWorldHint = false))
	public double customerBalance(@McpToolParam(description = "Demo customer ID", required = true) int customerId) {
		return this.db.customerBalance(customerId);
	}

	@McpTool(description = "Get the status of the customer's pending refund.",
			annotations = @McpAnnotations(readOnlyHint = true, openWorldHint = false))
	public String refundStatus(@McpToolParam(description = "Demo customer ID", required = true) int customerId) {
		return this.db.refundStatus(customerId);
	}

	@McpTool(description = "Search the bank policies for matching passages and their source filenames.",
			annotations = @McpAnnotations(readOnlyHint = true, openWorldHint = false))
	public List<PolicyPassage> searchBankPolicies(@McpToolParam(description = "Bank policy question", required = true) String query) {
		if (query == null || query.isBlank()) {
			throw new IllegalArgumentException("Enter a bank policy question.");
		}
		var request = SearchRequest.from(KnowledgeBase.SEARCH_REQUEST).query(query).build();
		return this.knowledgeBase.similaritySearch(request).stream()
			.map(doc -> new PolicyPassage((String) doc.getMetadata().get(TextReader.SOURCE_METADATA), doc.getText()))
			.toList();
	}

	public record PolicyPassage(String source, String text) {}

}
