package com.example.demo;

import java.util.Arrays;
import java.util.List;

import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;

/**
 * The bank's knowledge base for RAG: fee, transfer, card and overdraft documents,
 * embedded into an in-memory {@link SimpleVectorStore} at startup (a one-time, offline
 * step, as in the 05-rag demo). Each chunk keeps its file name in the
 * {@link TextReader#SOURCE_METADATA} metadata.
 *
 * A plain factory, not a component: the agent-facade main class does not scan this
 * package, so each application declares the store as its own bean.
 */
public final class KnowledgeBase {

	/**
	 * Ahead of the tool-calling advisor ({@code HIGHEST_PRECEDENCE + 300}), so the search
	 * runs once per question and not again on every round of the tool loop.
	 */
	public static final int ADVISOR_ORDER = Ordered.HIGHEST_PRECEDENCE + 200;

	/**
	 * At most three chunks, and only close ones. With the local all-MiniLM-L6-v2 model,
	 * the lost-card and card-abroad questions score 0.49 to 0.64 on their documents. The
	 * balance, refund and exchange-rate questions score 0.36 or less, so they get no chunk.
	 */
	public static final SearchRequest SEARCH_REQUEST = SearchRequest.builder().topK(3).similarityThreshold(0.45).build();

	/** The instruction that tells the model to answer policy questions from the retrieved context. */
	public static final String INSTRUCTIONS = "For questions about fees, transfers, cards and overdrafts, "
			+ "answer from the provided context. If the context does not cover the question, say so.";

	/**
	 * Replaces the advisor's default template. The default tells the model to refuse
	 * every question that the context does not answer, also when no chunk passes the
	 * threshold. That conflicts with the tools: the balance and refund questions get an
	 * empty context and must still be answered through the tools.
	 */
	private static final PromptTemplate PROMPT_TEMPLATE = new PromptTemplate("""
			{query}

			Bank documents that can be relevant to the question are below, surrounded by ---------------------

			---------------------
			{question_answer_context}
			---------------------

			Use these documents for questions about the bank's fees, transfers, cards and overdrafts.
			If they are empty or do not apply, answer with the tools and skills that are available.
			""");

	private KnowledgeBase() {
	}

	/**
	 * Reads the documents, splits them into chunks and adds the chunks to a new
	 * {@link SimpleVectorStore}.
	 */
	public static VectorStore load(EmbeddingModel embeddingModel, Resource[] documents) {
		List<Document> chunks = TokenTextSplitter.builder()
			.build()
			.split(Arrays.stream(documents).flatMap(document -> new TextReader(document).read().stream()).toList());
		VectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
		vectorStore.add(chunks);
		return vectorStore;
	}

	/**
	 * The RAG advisor: searches the knowledge base with the user's question and adds the
	 * matching chunks to the prompt.
	 */
	public static QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
		return QuestionAnswerAdvisor.builder(vectorStore)
			.searchRequest(SEARCH_REQUEST)
			.promptTemplate(PROMPT_TEMPLATE)
			.order(ADVISOR_ORDER)
			.build();
	}

}
