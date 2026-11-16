package com.example.demo.agent.capabilities;

import com.example.demo.KnowledgeBase;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * The RAG capability — the analog of a Pydantic AI capability that retrieves from a
 * vector store before each model request: it injects a {@link QuestionAnswerAdvisor}
 * into the agent's ChatClient. On every run the advisor searches the knowledge base with
 * the user's question and adds the matching chunks to the prompt.
 *
 * Unlike {@link SkillsCapability}, the model does not decide to load anything: the
 * retrieval always runs, and the similarity threshold decides what reaches the prompt.
 */
public final class RagCapability<D> implements Capability<D> {

	private final VectorStore vectorStore;

	/**
	 * @param vectorStore the knowledge base, see {@link KnowledgeBase#load}
	 */
	public RagCapability(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
	}

	@Override
	public String id() {
		return "rag";
	}

	@Override
	public void instrument(ChatClient.Builder chatClientBuilder) {
		chatClientBuilder.defaultAdvisors(KnowledgeBase.questionAnswerAdvisor(this.vectorStore));
	}

	@Override
	public String instructions(D deps) {
		return KnowledgeBase.INSTRUCTIONS;
	}

}
