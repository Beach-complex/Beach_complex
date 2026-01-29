package com.beachcheck.repository;

import com.beachcheck.domain.BeachCondition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeachConditionRepository extends JpaRepository<BeachCondition, UUID> {

  List<BeachCondition> findByBeachIdAndObservedAtAfter(
      UUID beachId, Instant observedAfter, Sort sort);
}
