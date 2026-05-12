package com.beachcheck.beach.repository;

import com.beachcheck.beach.domain.BeachFacility;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeachFacilityRepository extends JpaRepository<BeachFacility, UUID> {

  List<BeachFacility> findByBeachId(UUID beachId, Sort sort);
}
