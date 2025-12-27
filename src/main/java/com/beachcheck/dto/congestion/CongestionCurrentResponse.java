  package com.beachcheck.dto.congestion;                             
                                                                     
  import com.fasterxml.jackson.annotation.JsonIgnoreProperties;      
  import com.fasterxml.jackson.annotation.JsonProperty;              
  import java.time.Instant;                                          

  @JsonIgnoreProperties(ignoreUnknown = true)                        
  public record CongestionCurrentResponse(                           
          @JsonProperty("beach_id") String beachId,                  
          @JsonProperty("beach_name") String beachName,              
          @JsonProperty("input") InputContext input,                 
          @JsonProperty("rule_based") OutputBlock ruleBased,         
          @JsonProperty("ai") OutputBlock ai                         
  ) {                                                                
      @JsonIgnoreProperties(ignoreUnknown = true)                    
      public record InputContext(                                    
              @JsonProperty("timestamp") Instant timestamp,          
              @JsonProperty("weather") WeatherInput weather,         
              @JsonProperty("is_weekend_or_holiday") Boolean         
  isWeekendOrHoliday                                                 
      ) {}                                                           
                                                                     
      @JsonIgnoreProperties(ignoreUnknown = true)                    
      public record WeatherInput(                                    
              @JsonProperty("temp_c") Double tempC,                  
              @JsonProperty("rain_mm") Double rainMm,
              @JsonProperty("wind_mps") Double windMps               
      ) {}                                                           
                                                                     
      @JsonIgnoreProperties(ignoreUnknown = true)                    
      public record OutputBlock(                                     
              @JsonProperty("score_raw") Double scoreRaw,            
              @JsonProperty("score_pct") Double scorePct,            
              @JsonProperty("level") String level,                   
              @JsonProperty("model_version") String modelVersion     
      ) {}                                                           
  }
  