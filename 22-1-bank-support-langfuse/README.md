# Bank Support Agent with Langfuse

The classic `BankSupportApplication` from [22-bank-support-agent](../22-bank-support-agent/README.md), with its Spring AI
observations exported to [Langfuse](https://langfuse.com). The agent, its tools, the refunds skill, the RAG advisor, the
exchange-rate MCP server and the two scripted conversations are unchanged. The agent-facade variant is not copied.

Langfuse's [Spring AI guide](https://langfuse.com/integrations/frameworks/spring-ai) describes the setup a team would add
to an existing app. This module follows it with Spring Boot 4's own OpenTelemetry starter:

| Change | Where |
|---|---|
| `spring-boot-starter-opentelemetry`: Micrometer Tracing on the OpenTelemetry SDK, with an OTLP/HTTP span exporter | `pom.xml` |
| OTLP endpoint, Basic auth header and `x-langfuse-ingestion-version=4`; sampling at 100%; OTLP metrics off | `application.properties` |
| `gen_ai.prompt` and `gen_ai.completion` on each chat model span, so generations show input and output | `LangfuseContentObservationFilter` |
| Tool arguments and results on the tool spans (`spring.ai.tools.observations.include-content`), set as each tool observation's input and output; Langfuse otherwise lists them only under metadata | `application.properties`, `LangfuseContentObservationFilter` |
| One root span per question with `langfuse.session.id`, `langfuse.user.id`, input and output, so each customer's conversation is a Langfuse session | `BankSupportApplication` |

## Running

Prerequisites are the same as for module 22 (Gemini access, Node.js and `npx`), plus Docker.

Start a local Langfuse. The first start pulls the images and takes two or three minutes:

```bash
docker compose -f 22-1-bank-support-langfuse/docker/compose.yaml up -d
curl -s http://localhost:3000/api/public/health    # {"status":"OK",...} once it is ready
```

The compose file creates the organization, a `bank-support` project with the API keys
`pk-lf-bank-support-demo` / `sk-lf-bank-support-demo`, and the user `demo@example.com` with the password
`bank-support-demo`. The app's defaults already point at this instance, so nothing else needs to be set.

Run the app:

```bash
mvn spring-boot:run -pl 22-1-bank-support-langfuse
```

From IntelliJ IDEA, run or debug `BankSupportApplication` in this module. The Debug launch also feeds the AIR app trace,
because the actuator supplies the observation registry both tools use.

The exchange-rate MCP server has 20 seconds to start. If the first start fails with `Client failed to initialize by
explicit API call`, `npx` was still downloading the package; run the app again.

Open http://localhost:3000, sign in, and open the `bank-support` project:

- **Sessions** lists `support-session-customer-123` (John, five questions) and `support-session-customer-456` (two).
- **Traces** has one trace per question. Each trace starts with `support question` and contains the chat client span,
  the advisor spans, one generation per model round-trip and a span per tool call, including `fx_convert_currency`
  from the MCP server. For "What is my balance in euros?", that tool's input is the USD amount and its output the EUR
  result from the ECB rate.
- Five short `embedding` traces at startup come from embedding the knowledge base, outside any question.

Stop Langfuse with `docker compose -f 22-1-bank-support-langfuse/docker/compose.yaml down`. Add `-v` to delete the
stored traces as well.

## Langfuse Cloud

Create a project, then set the endpoint for your region and the Basic auth value before starting the app:

```bash
export LANGFUSE_OTLP_ENDPOINT=https://cloud.langfuse.com/api/public/otel/v1/traces    # EU; US: us.cloud.langfuse.com
export LANGFUSE_AUTH=$(printf '%s' 'pk-lf-...:sk-lf-...' | base64)
```

Prompts, completions, tool arguments and customer data leave the machine in this setup.

## What Langfuse shows and what it leaves out

Checked on a local run with Langfuse 4.83.0:

- Advisor spans (`question_answer`, `session_memory`, `my_logging`) carry a name and an order but no content. They show
  that the advisors ran, not what each one added to the prompt.
- Retrieved documents appear only inside the generation input, as part of the user message the RAG advisor rewrote.
  There is no retrieval step with the query, `topK`, the similarity threshold or the scores, and the `embedding`
  generation for the query has no input or output. The `SimpleVectorStore` emits no span of its own.
- The generation input is the joined message text from Langfuse's filter, without roles. Earlier turns from session
  memory, including their retrieved documents, are repeated in it.
- A round-trip that ends in tool calls shows an empty output (`[]`), because the filter keeps only completion text.
  The tool spans that follow show which tools ran.
- Nothing links a span to the Spring bean or source line that configured the step.

Ports 3000 and 9090 are also used by the [24-observability](../24-observability/README.md) stack (Grafana and
Prometheus), so run one stack at a time.
