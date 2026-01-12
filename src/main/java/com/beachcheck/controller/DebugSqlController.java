package com.beachcheck.controller;

import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequestMapping("/api/_debug")
public class DebugSqlController {
  private final JdbcTemplate jdbc;

  public DebugSqlController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/db")
  public Map<String, Object> db() {
    return jdbc.queryForMap("select current_database() as db, current_schema() as schema");
  }

  @GetMapping("/beaches-sql")
  public List<Map<String, Object>> beachesSql() {
    return jdbc.queryForList("select id, code, name, tag from public.beaches order by name");
  }

  @GetMapping("/beaches-count")
  public Map<String, Object> beachesCount() {
    return jdbc.queryForMap("select count(*) as cnt from public.beaches");
  }
}
