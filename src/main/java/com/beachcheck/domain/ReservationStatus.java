package com.beachcheck.domain;

/**
 * Why: 예약 상태를 제한된 값 집합으로 고정해 일관성을 보장하기 위해. Policy: 상태는 enum 상수로만 표현한다. Contract(Input): 상태 타입으로
 * ReservationStatus를 사용한다. Contract(Output): 가능한 값은 CONFIRMED 또는 REJECTED다.
 */
public enum ReservationStatus {
  CONFIRMED,
  REJECTED
}
