package com.example.demo;

import com.example.demo.service.AgentService;
import com.example.demo.service.AiClient;
import com.example.demo.service.RagService;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

@RestController
@CrossOrigin(origins = "*")
public class AskController {

  private static final long MAX_FILE_BYTES = 1_000_000; // ~1 MB

  private final AiClient aiClient;
  private final RagService ragService;
  private final AgentService agentService;

  public AskController(AiClient aiClient, RagService ragService, AgentService agentService) {
    this.aiClient = aiClient;
    this.ragService = ragService;
    this.agentService = agentService;
  }

  @PostMapping("/api/ask")
  public Mono<AskResponse> ask(@RequestBody(required = false) AskRequest request) {
    String prompt =
        request != null && request.question() != null && !request.question().isBlank()
            ? request.question().trim()
            : "Say hello to the user.";
    return ragService.answerWithContext(prompt).map(AskResponse::new);
  }

  @PostMapping(value = "/api/rag/ingest", consumes = "application/json")
  public Mono<AskResponse> ingest(@RequestBody IngestRequest request) {
    if (request == null || request.text() == null || request.text().isBlank()) {
      return Mono.just(new AskResponse("No text provided."));
    }
    var source = request.source() == null || request.source().isBlank() ? "manual" : request.source();
    return ragService.ingest(request.text(), source).map(AskResponse::new);
  }

  @PostMapping(value = "/api/rag/ingest-file", consumes = "multipart/form-data")
  public Mono<AskResponse> ingestFile(@RequestPart("file") MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return Mono.just(new AskResponse("No file uploaded."));
    }
    if (file.getSize() > MAX_FILE_BYTES) {
      return Mono.just(new AskResponse("File too large (limit ~1MB)."));
    }
    var type = file.getContentType();
    if (type != null && !type.startsWith("text")) {
      return Mono.just(new AskResponse("Only text files are supported for now."));
    }

    try {
      var content = new String(file.getBytes(), StandardCharsets.UTF_8);
      if (content.isBlank()) {
        return Mono.just(new AskResponse("File was empty."));
      }
      var source = file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded-file";
      return ragService.ingest(content, source).map(AskResponse::new);
    } catch (Exception e) {
      return Mono.just(new AskResponse("Failed to read the file."));
    }
  }

  @PostMapping(value = "/api/agent/ask", consumes = "application/json")
  public Mono<AskResponse> agentAsk(@RequestBody(required = false) AskRequest request) {
    String prompt =
        request != null && request.question() != null && !request.question().isBlank()
            ? request.question().trim()
            : "Say hello to the user.";
    return agentService.ask(prompt).map(AskResponse::new);
  }

  @PostMapping(value = "/api/summarize-file", consumes = "multipart/form-data")
  public Mono<AskResponse> summarizeFile(@RequestPart("file") MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return Mono.just(new AskResponse("No file uploaded."));
    }
    if (file.getSize() > MAX_FILE_BYTES) {
      return Mono.just(new AskResponse("File too large (limit ~1MB)."));
    }
    var type = file.getContentType();
    if (type != null && !type.startsWith("text")) {
      return Mono.just(new AskResponse("Only text files are supported for now."));
    }

    try {
      var content = new String(file.getBytes(), StandardCharsets.UTF_8);
      if (content.isBlank()) {
        return Mono.just(new AskResponse("File was empty."));
      }
      var prompt =
          "Summarize this file in 2-3 sentences, max 60 words. Be concise and avoid filler:\n\n"
              + content;
      return aiClient
          .generate(prompt)
          .map(AskResponse::new)
          .onErrorResume(err -> Mono.just(new AskResponse("Could not summarize the file.")));
    } catch (Exception e) {
      return Mono.just(new AskResponse("Failed to read the file."));
    }
  }

  public record AskRequest(String question) {}

  public record AskResponse(String answer) {}

  public record IngestRequest(String text, String source) {}
}
