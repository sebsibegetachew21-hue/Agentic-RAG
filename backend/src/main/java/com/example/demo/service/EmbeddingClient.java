package com.example.demo.service;

import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class EmbeddingClient {

  private final WebClient aiWebClient;
  private final String embedModel;

  public EmbeddingClient(
      WebClient aiWebClient, @Value("${ai.embed.model:nomic-embed-text}") String embedModel) {
    this.aiWebClient = aiWebClient;
    this.embedModel = embedModel;
  }

  public Mono<float[]> embed(String text) {
    // Ollama embeddings endpoint expects "prompt"
    var body = Map.of("model", embedModel, "prompt", text);
    return aiWebClient
        .post()
        .uri("/api/embeddings")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .retrieve()
        .bodyToMono(EmbedResponse.class)
        .timeout(Duration.ofSeconds(12))
        .map(this::toFloats);
  }

  private float[] toFloats(EmbedResponse response) {
    if (response == null || response.embedding == null) {
      throw new IllegalStateException("Embedding response was empty");
    }
    var floats = new float[response.embedding.length];
    for (int i = 0; i < response.embedding.length; i++) {
      floats[i] = response.embedding[i].floatValue();
    }
    return floats;
  }

  public record EmbedResponse(Float[] embedding, String model) {}
}
