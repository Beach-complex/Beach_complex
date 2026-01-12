package com.beachcheck.controller;

import com.beachcheck.dto.beach.BeachFacilityDto;
import com.beachcheck.service.BeachFacilityService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/beaches/{beachId}/facilities")
@Validated
public class BeachFacilityController {

  private final BeachFacilityService beachFacilityService;

  public BeachFacilityController(BeachFacilityService beachFacilityService) {
    this.beachFacilityService = beachFacilityService;
  }

  @GetMapping
  public ResponseEntity<List<BeachFacilityDto>> findFacilities(
      @PathVariable @NotNull UUID beachId) {
    return ResponseEntity.ok(beachFacilityService.findByBeachId(beachId));
  }

  // TODO: Introduce PATCH endpoint for facility maintenance window updates.
}
