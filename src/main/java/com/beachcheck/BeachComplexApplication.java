package com.beachcheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.beachcheck.domain")
@EnableJpaRepositories(basePackages = "com.beachcheck.repository")
public class BeachComplexApplication {
  public static void main(String[] args) {

    SpringApplication.run(BeachComplexApplication.class, args);
  }
}
