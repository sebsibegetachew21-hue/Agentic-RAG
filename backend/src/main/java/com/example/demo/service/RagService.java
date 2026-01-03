package com.example.demo.service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class RagService {

  private final EmbeddingClient embeddingClient;
  private final AiClient aiClient;
  private final StringRedisTemplate redisTemplate;
  private final String indexName;
  private final int topK;

  /**
   * Exposes existing retrieval for agent use.
   */
  public Mono<List<String>> retrieveContexts(String question) {
    if (question == null || question.isBlank()) {
      return Mono.just(List.of());
    }
    return embeddingClient
        .embed(question)
        .flatMap(
            vec ->
                Mono.fromCallable(() -> search(vec)).subscribeOn(Schedulers.boundedElastic()))
        .defaultIfEmpty(List.of());
  }

  public RagService(
      EmbeddingClient embeddingClient,
      AiClient aiClient,
      StringRedisTemplate redisTemplate,
      @Value("${rag.index:rag:docs}") String indexName,
      @Value("${rag.top-k:3}") int topK) {
    this.embeddingClient = embeddingClient;
    this.aiClient = aiClient;
    this.redisTemplate = redisTemplate;
    this.indexName = indexName;
    this.topK = topK;
  }

  public Mono<String> ingest(String content, String source) {
    if (content == null || content.isBlank()) {
      return Mono.just("No content to ingest.");
    }
    var chunks = chunk(content, 600, 120);
    if (chunks.isEmpty()) {
      return Mono.just("No content to ingest.");
    }
    var ensured = new AtomicBoolean(false);
    return Flux.fromIterable(chunks)
        .flatMap(
            chunk ->
                embeddingClient
                    .embed(chunk)
                    .flatMap(
                        vec ->
                            Mono.fromCallable(
                                    () -> {
                                      ensureIndexOnce(vec.length, ensured);
                                      storeChunk(chunk, source, vec);
                                      return 1;
                                    })
                                .subscribeOn(Schedulers.boundedElastic()))
                    .onErrorResume(
                        err -> {
                          Thread.currentThread().interrupt();
                          return Mono.empty();
                        }))
        .count()
        .map(count -> "Ingested %d chunks from %s".formatted(count, source));
  }

  public Mono<String> answerWithContext(String question) {
    if (question == null || question.isBlank()) {
      return Mono.just("No question provided.");
    }
    return embeddingClient
        .embed(question)
        .flatMap(
            vec ->
                Mono.fromCallable(() -> search(vec))
                    .subscribeOn(Schedulers.boundedElastic())
                    .defaultIfEmpty(List.of())
                    .flatMap(
                        contexts -> {
                          var prompt = buildPrompt(question, contexts);
                          return aiClient.generate(prompt);
                        }));
  }

  private void ensureIndexOnce(int dim, AtomicBoolean ensured) {
    if (ensured.get()) {
      return;
    }
    synchronized (ensured) {
      if (ensured.get()) {
        return;
      }
      ensureIndex(dim);
      ensured.set(true);
    }
  }

  private void ensureIndex(int dim) {
    try {
      redisTemplate.execute(
          (RedisCallback<Void>)
              conn -> {
                try {
                  conn.execute("FT.INFO", indexName.getBytes(StandardCharsets.UTF_8));
                  return null;
                } catch (Exception e) {
                  // create the index if it does not exist
                  conn.execute(
                      "FT.CREATE",
                      indexName.getBytes(StandardCharsets.UTF_8),
                      "ON".getBytes(StandardCharsets.UTF_8),
                      "HASH".getBytes(StandardCharsets.UTF_8),
                      "PREFIX".getBytes(StandardCharsets.UTF_8),
                      "1".getBytes(StandardCharsets.UTF_8),
                      "rag:doc:".getBytes(StandardCharsets.UTF_8),
                      "SCHEMA".getBytes(StandardCharsets.UTF_8),
                      "content".getBytes(StandardCharsets.UTF_8),
                      "TEXT".getBytes(StandardCharsets.UTF_8),
                      "source".getBytes(StandardCharsets.UTF_8),
                      "TAG".getBytes(StandardCharsets.UTF_8),
                      "embedding".getBytes(StandardCharsets.UTF_8),
                      "VECTOR".getBytes(StandardCharsets.UTF_8),
                      "HNSW".getBytes(StandardCharsets.UTF_8),
                      "6".getBytes(StandardCharsets.UTF_8),
                      "TYPE".getBytes(StandardCharsets.UTF_8),
                      "FLOAT32".getBytes(StandardCharsets.UTF_8),
                      "DIM".getBytes(StandardCharsets.UTF_8),
                      String.valueOf(dim).getBytes(StandardCharsets.UTF_8),
                      "DISTANCE_METRIC".getBytes(StandardCharsets.UTF_8),
                      "COSINE".getBytes(StandardCharsets.UTF_8));
                  return null;
                }
              });
    } catch (Exception e) {
      // Avoid failing startup/ingest if the reactive pipeline gets cancelled while blocking.
      Thread.currentThread().interrupt();
    }
  }

  private void storeChunk(String content, String source, float[] embedding) {
    var id = "rag:doc:" + UUID.randomUUID();
    var vec = float32ToBytes(embedding);
    try {
      redisTemplate.execute(
          (RedisCallback<Void>)
              conn -> {
                try {
                  conn.hSet(
                      id.getBytes(StandardCharsets.UTF_8),
                      "content".getBytes(StandardCharsets.UTF_8),
                      content.getBytes(StandardCharsets.UTF_8));
                  conn.hSet(
                      id.getBytes(StandardCharsets.UTF_8),
                      "source".getBytes(StandardCharsets.UTF_8),
                      source.getBytes(StandardCharsets.UTF_8));
                  conn.hSet(
                      id.getBytes(StandardCharsets.UTF_8),
                      "embedding".getBytes(StandardCharsets.UTF_8),
                      vec);
                } catch (Exception e) {
                  Thread.currentThread().interrupt();
                }
                return null;
              });
    } catch (Exception e) {
      Thread.currentThread().interrupt();
    }
  }

  private List<String> search(float[] embedding) {
    var vec = float32ToBytes(embedding);
    return redisTemplate.execute(
        (RedisCallback<List<String>>)
            connection -> {
              if (!(connection instanceof org.springframework.data.redis.connection.lettuce.LettuceConnection
                  lettuceConnection)) {
                return List.of();
              }
              var nativeConn =
                  (io.lettuce.core.api.StatefulRedisConnection<byte[], byte[]>)
                      lettuceConnection.getNativeConnection();
              var args =
                  new io.lettuce.core.protocol.CommandArgs<>(io.lettuce.core.codec.ByteArrayCodec.INSTANCE)
                      .add(indexName.getBytes(StandardCharsets.UTF_8))
                      .add(
                          "*=>[KNN %d @embedding $vec_param]".formatted(Math.max(1, topK))
                              .getBytes(StandardCharsets.UTF_8))
                      .add("PARAMS".getBytes(StandardCharsets.UTF_8))
                      .add("2".getBytes(StandardCharsets.UTF_8))
                      .add("vec_param".getBytes(StandardCharsets.UTF_8))
                      .add(vec)
                      .add("RETURN".getBytes(StandardCharsets.UTF_8))
                      .add("2".getBytes(StandardCharsets.UTF_8))
                      .add("content".getBytes(StandardCharsets.UTF_8))
                      .add("source".getBytes(StandardCharsets.UTF_8))
                      .add("DIALECT".getBytes(StandardCharsets.UTF_8))
                      .add("2".getBytes(StandardCharsets.UTF_8));

              var output =
                  new io.lettuce.core.output.NestedMultiOutput<>(
                      io.lettuce.core.codec.ByteArrayCodec.INSTANCE);
              io.lettuce.core.protocol.Command<byte[], byte[], List<Object>> command =
                  new io.lettuce.core.protocol.Command<>(
                      io.lettuce.core.protocol.CommandType.valueOf("FT.SEARCH"), output, args);
              nativeConn.dispatch(command);
              List<Object> result = command.getOutput().get();
              if (result == null || result.size() < 2) {
                return List.of();
              }

              var contexts = new ArrayList<String>();
              for (int i = 1; i < result.size(); i += 2) {
                if (i + 1 >= result.size()) {
                  break;
                }
                var fieldsObj = result.get(i + 1);
                if (fieldsObj instanceof List<?> fields) {
                  for (int j = 0; j < fields.size() - 1; j += 2) {
                    var name = toString(fields.get(j));
                    var val = toString(fields.get(j + 1));
                    if ("content".equals(name)) {
                      contexts.add(val);
                    }
                  }
                }
              }
              return contexts;
            });
  }

  private String buildPrompt(String question, List<String> contexts) {
    var sb = new StringBuilder();
    if (contexts != null && !contexts.isEmpty()) {
      sb.append(
          "Answer strictly from the context below. Do NOT add any facts or names not present. "
              + "If the answer is not explicitly in the context, reply with \"I don't know.\" "
              + "Do not use outside knowledge.\n\n");
      for (int i = 0; i < contexts.size(); i++) {
        sb.append("- Chunk ").append(i + 1).append(": ").append(contexts.get(i)).append("\n");
      }
      sb.append("\nRespond in 1-2 sentences using only the context. No extra details. ");
    } else {
      sb.append("Answer concisely. ");
    }
    sb.append("Question: ").append(question);
    return sb.toString();
  }

  private static List<String> chunk(String text, int size, int overlap) {
    var normalized = text.trim();
    if (normalized.isEmpty()) {
      return List.of();
    }
    var chunks = new ArrayList<String>();
    int start = 0;
    while (start < normalized.length()) {
      int end = Math.min(normalized.length(), start + size);
      chunks.add(normalized.substring(start, end));
      if (end == normalized.length()) {
        break;
      }
      start = Math.max(end - overlap, start + 1);
    }
    return chunks;
  }

  private static byte[] float32ToBytes(float[] vector) {
    var buf = ByteBuffer.allocate(vector.length * 4).order(ByteOrder.LITTLE_ENDIAN);
    for (float v : vector) {
      buf.putFloat(v);
    }
    return buf.array();
  }

  private static String toString(Object obj) {
    if (obj instanceof byte[] bytes) {
      return new String(bytes, StandardCharsets.UTF_8);
    }
    return obj != null ? obj.toString() : "";
  }
}
