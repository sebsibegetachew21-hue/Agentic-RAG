package com.example.demo.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class AiClient {

  private final WebClient aiWebClient;
  private final String apiKey;
  private final String model;

  public AiClient(
      WebClient aiWebClient,
      @Value("${ai.api.key:}") String apiKey,
      @Value("${ai.model:mistral}") String model) {
    this.aiWebClient = aiWebClient;
    this.apiKey = apiKey;
    this.model = model;
  }

  /**
   * Placeholder call for now. Keeps the app running without a real provider.
   */
  public Mono<String> generate(String prompt) {
    var messages =
        List.of(
            Map.of("role", "system", "content", "You are a concise assistant."),
            Map.of("role", "user", "content", prompt));

    // If no API key, assume local provider like Ollama on ai.api.url.
    if (apiKey == null || apiKey.isBlank()) {
      var body = Map.of("model", model, "messages", messages, "stream", false);
      return aiWebClient
          .post()
          .uri("/api/chat")
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON)
          .bodyValue(body)
          .retrieve()
          .bodyToMono(OllamaChatResponse.class)
          .timeout(Duration.ofSeconds(12))
          .map(this::extractOllamaAnswer)
          .onErrorResume(
              err ->
                  Mono.just(
                      "Local AI call failed (check ai.api.url/model). Fallback response. Prompt: "
                          + prompt));
    }

    var body =
        Map.of(
            "model", model,
            "messages", messages);

    return aiWebClient
        .post()
        .uri("/v1/chat/completions")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .retrieve()
        .bodyToMono(ChatCompletionResponse.class)
        .timeout(Duration.ofSeconds(12))
        .map(this::extractAnswer)
        .onErrorResume(
            err ->
                Mono.just(
                    "AI call failed (check ai.api.url/key/model). Showing fallback. Prompt: " + prompt));
  }

  private String extractAnswer(ChatCompletionResponse response) {
    if (response == null || response.choices == null || response.choices.isEmpty()) {
      return "AI returned no content.";
    }
    var msg = response.choices.get(0).message;
    return msg != null && msg.content != null ? msg.content : "AI returned no content.";
  }

  public record ChatCompletionResponse(List<Choice> choices) {}

  public record Choice(Message message) {}

  public record Message(String role, String content) {}

  public record OllamaChatResponse(OllamaMessage message) {}

  public record OllamaMessage(String role, String content) {}

  private String extractOllamaAnswer(OllamaChatResponse response) {
    if (response == null || response.message == null || response.message.content == null) {
      return "AI returned no content.";
    }
    return response.message.content();
  }
}
