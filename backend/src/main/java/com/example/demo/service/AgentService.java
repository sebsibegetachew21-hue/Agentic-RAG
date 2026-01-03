package com.example.demo.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AgentService {

  private final RagService ragService;
  private final ChatLanguageModel model;

  public AgentService(
      RagService ragService,
      @Value("${ai.api.url:http://localhost:11434}") String baseUrl,
      @Value("${ai.model:phi3:mini}") String modelName) {
    this.ragService = ragService;
    this.model =
        OllamaChatModel.builder()
            .baseUrl(baseUrl)
            .modelName(modelName)
            .timeout(Duration.ofSeconds(20))
            .build();
  }

  public Mono<String> ask(String question) {
    return ragService
        .retrieveContexts(question)
        .flatMap(
            contexts ->
                Mono.fromCallable(
                    () -> {
                      var prompt = buildPrompt(question, contexts);
                      return model.generate(prompt);
                    }));
  }

  private String buildPrompt(String question, List<String> contexts) {
    var sb = new StringBuilder();
    sb.append(
        "You are an agent that must answer only using the provided context. "
            + "If the answer is not present in the context, reply exactly with \"I don't know.\" "
            + "Do not mention missing context. Keep answers to 1-2 sentences.\n\n");
    if (contexts != null && !contexts.isEmpty()) {
      sb.append("Context:\n");
      for (int i = 0; i < contexts.size(); i++) {
        sb.append("- Chunk ").append(i + 1).append(": ").append(contexts.get(i)).append("\n");
      }
    } else {
      sb.append("Context: (none)\n");
    }
    sb.append("\nQuestion: ").append(question);
    return sb.toString();
  }
}
