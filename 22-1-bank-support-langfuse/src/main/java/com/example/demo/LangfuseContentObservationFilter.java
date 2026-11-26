package com.example.demo;

import java.util.List;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Content;
import org.springframework.ai.observation.ObservabilityHelper;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Puts the chat model's prompt and completion on the model span as {@code gen_ai.prompt} and
 * {@code gen_ai.completion}, which Langfuse shows as the generation's input and output.
 * Spring AI does not add them itself: without this filter the generations arrive in Langfuse
 * with empty input and output.
 *
 * The chat model part follows the filter in Langfuse's Spring AI guide
 * (https://langfuse.com/integrations/frameworks/spring-ai): the message texts are joined, so
 * roles, tool calls and tool responses are not shown separately.
 *
 * Tool spans: {@code spring.ai.tools.observations.include-content} puts the arguments and the
 * result on the span, where Langfuse lists them only under metadata. This filter also sets them
 * as the tool observation's input and output.
 */
@Component
class LangfuseContentObservationFilter implements ObservationFilter {

	@Override
	public Observation.Context map(Observation.Context context) {
		if (context instanceof ToolCallingObservationContext toolContext) {
			return toolContent(toolContext);
		}
		if (!(context instanceof ChatModelObservationContext chatModelContext)) {
			return context;
		}
		List<String> prompts = prompts(chatModelContext.getRequest());
		List<String> completions = completions(chatModelContext.getResponse());
		chatModelContext.addHighCardinalityKeyValue(
				KeyValue.of("gen_ai.prompt", ObservabilityHelper.concatenateStrings(prompts)));
		chatModelContext.addHighCardinalityKeyValue(
				KeyValue.of("gen_ai.completion", ObservabilityHelper.concatenateStrings(completions)));
		return chatModelContext;
	}

	private static Observation.Context toolContent(ToolCallingObservationContext context) {
		if (StringUtils.hasText(context.getToolCallArguments())) {
			context.addHighCardinalityKeyValue(KeyValue.of("langfuse.observation.input", context.getToolCallArguments()));
		}
		if (StringUtils.hasText(context.getToolCallResult())) {
			context.addHighCardinalityKeyValue(KeyValue.of("langfuse.observation.output", context.getToolCallResult()));
		}
		return context;
	}

	private static List<String> prompts(Prompt prompt) {
		if (prompt == null || CollectionUtils.isEmpty(prompt.getInstructions())) {
			return List.of();
		}
		return prompt.getInstructions().stream().map(Content::getText).filter(StringUtils::hasText).toList();
	}

	private static List<String> completions(ChatResponse response) {
		if (response == null || CollectionUtils.isEmpty(response.getResults())) {
			return List.of();
		}
		return response.getResults()
			.stream()
			.map(generation -> generation.getOutput().getText())
			.filter(StringUtils::hasText)
			.toList();
	}

}
