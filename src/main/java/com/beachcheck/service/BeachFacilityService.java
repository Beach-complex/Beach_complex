package com.beachcheck.service;

import com.beachcheck.domain.BeachFacility;
import com.beachcheck.dto.beach.BeachFacilityDto;
import com.beachcheck.repository.BeachFacilityRepository;
import com.beachcheck.util.GeometryUtils;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BeachFacilityService {

  private static final String SORT_FACILITY_NAME = "name";

  private final BeachFacilityRepository beachFacilityRepository;

  public BeachFacilityService(BeachFacilityRepository beachFacilityRepository) {
    this.beachFacilityRepository = beachFacilityRepository;
  }

  @Cacheable(value = "facilitySummaries", key = "#beachId")
  public List<BeachFacilityDto> findByBeachId(UUID beachId) {
    Sort sort = Sort.by(Sort.Order.asc(SORT_FACILITY_NAME).ignoreCase());
    return beachFacilityRepository.findByBeachId(beachId, sort).stream().map(this::toDto).toList();
  }

  private BeachFacilityDto toDto(BeachFacility facility) {
    return new BeachFacilityDto(
        facility.getId(),
        facility.getBeach().getId(),
        facility.getName(),
        facility.getCategory(),
        GeometryUtils.extractLatitude(facility.getLocation()),
        GeometryUtils.extractLongitude(facility.getLocation()));
  }

  // TODO: Sync facility catalog with asset management platform API when credentials are available.
}
