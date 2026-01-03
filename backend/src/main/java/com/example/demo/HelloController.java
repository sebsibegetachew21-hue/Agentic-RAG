package com.example.demo;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

  @CrossOrigin(origins = "*")
  @GetMapping("/api/hello")
  public Greeting hello() {
    return new Greeting("Hello from Spring Boot!");
  }

  public record Greeting(String message) {}
}  