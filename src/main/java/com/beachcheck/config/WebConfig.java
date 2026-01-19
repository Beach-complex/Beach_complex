package com.beachcheck.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {

  @Bean
  public WebMvcConfigurer webMvcConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry
            .addMapping("/api/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*");
      }
    };
  }

  @Bean
  public Jackson2ObjectMapperBuilder jackson2ObjectMapperBuilder() {
    return Jackson2ObjectMapperBuilder.json()
        .failOnUnknownProperties(false)
        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  // TODO: Externalize allowed origins to configuration management service when available.
}
