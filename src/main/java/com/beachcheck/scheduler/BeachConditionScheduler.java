  package com.beachcheck.scheduler;                                     
                                                                        
  import com.beachcheck.client.CongestionClient;                        
  import com.beachcheck.domain.Beach;                                   
  import com.beachcheck.domain.BeachCondition;                          
  import com.beachcheck.dto.congestion.CongestionCurrentResponse;       
  import com.beachcheck.repository.BeachConditionRepository;            
  import com.beachcheck.repository.BeachRepository;                     
  import org.slf4j.Logger;                                              
  import org.slf4j.LoggerFactory;                                       
  import org.springframework.beans.factory.annotation.Value;            
  import org.springframework.scheduling.annotation.Scheduled;           
  import org.springframework.stereotype.Component;                      
                                                                        
  import java.time.Instant;                                             
  import java.util.List;                                                
                                                                        
  @Component                                                            
  public class BeachConditionScheduler {                                
                                                                        
      private static final Logger log =                                 
  LoggerFactory.getLogger(BeachConditionScheduler.class);               
                                                                        
      private final BeachRepository beachRepository;                    
      private final BeachConditionRepository beachConditionRepository;  
      private final CongestionClient congestionClient;                  
      private final String mode;                                        
                                                                        
      public BeachConditionScheduler(                                   
              BeachRepository beachRepository,                          
              BeachConditionRepository beachConditionRepository,        
              CongestionClient congestionClient,                        
              @Value("${app.congestion.mode:ai}") String mode           
      ) {                                                               
          this.beachRepository = beachRepository;                       
          this.beachConditionRepository = beachConditionRepository;     
          this.congestionClient = congestionClient;                     
          this.mode = mode;                                             
      }                                                                 
                                                                        
      @Scheduled(cron = "0 0/30 * * * *")                               
      public void refreshConditions() {                                 
          log.info("Scheduled condition refresh triggered");            
                                                                        
          List<Beach> beaches = beachRepository.findAll();              
          for (Beach beach : beaches) {                                 
              String code = beach.getCode();                            
              if (code == null || code.isBlank()) {                     
                  log.warn("Skip beach with missing code. beachId={}",  
  beach.getId());                                                       
                  continue;                                             
              }                                                         
                                                                        
              CongestionCurrentResponse response =                      
  congestionClient.fetchCurrent(code);                                  
              if (response == null) {                                   
                  continue;                                             
              }                                                         
                                                                        
              Instant observedAt = Instant.now();                       
              Double tempC = null;                                      
              Double rainMm = null;                                     
              Double windMps = null;                                    
                                                                        
              if (response.input() != null) {                           
                  if (response.input().timestamp() != null) {           
                      observedAt = response.input().timestamp();        
                  }                                                     
                  if (response.input().weather() != null) {             
                      tempC = response.input().weather().tempC();       
                      rainMm = response.input().weather().rainMm();     
                      windMps = response.input().weather().windMps();   
                  }
              }                                                         
                                                                        
              BeachCondition condition = new BeachCondition();          
              condition.setBeach(beach);                                
              condition.setObservedAt(observedAt);                      
              condition.setWaterTemperatureCelsius(tempC);              
              condition.setWaveHeightMeters(null);                      
              condition.setWeatherSummary(formatWeatherSummary(tempC,   
  rainMm, windMps));                                                    
              condition.setObservationPoint(beach.getLocation());       
              beachConditionRepository.save(condition);                 
                                                                        
              String level = resolveLevel(response);                    
              String status = mapStatus(level);                         
              if (status != null && !                                   
  status.equalsIgnoreCase(beach.getStatus())) {                         
                  beach.setStatus(status);                              
                  beachRepository.save(beach);                          
              }                                                         
          }                                                             
      }                                                                 
                                                                        
      private String resolveLevel(CongestionCurrentResponse response) { 
          if ("rule_based".equalsIgnoreCase(mode) || "rule-based".equalsIgnoreCase(mode)) {                                      
              return response.ruleBased() != null ? response.ruleBased().level() : null;                                  
          }                                                             
          return response.ai() != null ? response.ai().level() : null;  
      }                                                                 
                                                                        
      private String mapStatus(String level) {                          
          if (level == null) return null;                               
          return switch (level.toLowerCase()) {                         
              case "low" -> "free";                                     
              case "medium" -> "normal";                                
              case "high" -> "busy";                                    
              default -> null;                                          
          };                                                            
      }                                                                 
                                                                        
      private String formatWeatherSummary(Double tempC, Double rainMm, Double windMps) {                                                     
          String temp = tempC == null ? "n/a" : String.format("%.1fC", tempC);                                                               
          String rain = rainMm == null ? "n/a" : String.format("%.1fmm", rainMm);                                                              
          String wind = windMps == null ? "n/a" : String.format("%.1fm/s", windMps);                                                         
          return String.format("temp:%s, rain:%s, wind:%s", temp, rain, wind);                                                                
      }                                                                 
  }
