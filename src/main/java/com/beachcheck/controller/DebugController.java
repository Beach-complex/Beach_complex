package com.beachcheck.controller;

import com.beachcheck.repository.BeachRepository;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
public class DebugController {
  private final BeachRepository repo;

  public DebugController(BeachRepository repo) {
    this.repo = repo;
  }

  @GetMapping("/api/_debug/beaches-all")
  public List<Map<String, Object>> all() {
    return repo.findAll().stream()
        .map(
            b -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("id", b.getId());
              m.put("code", b.getCode());
              m.put("name", b.getName());
              // m.put("tag", b.getTag());  // 필요하면 복구
              return m;
            })
        .toList();
  }
}
