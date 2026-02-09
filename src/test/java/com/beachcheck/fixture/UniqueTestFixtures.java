package com.beachcheck.fixture;

import java.util.UUID;

public final class UniqueTestFixtures {

  private UniqueTestFixtures() {}

  public static String uniqueCode(String prefix) {
    return prefix + "_" + shortId();
  }

  public static String uniqueBeachCode() {
    return uniqueCode("TEST_BEACH");
  }

  public static String uniqueEmail(String prefix) {
    return prefix + "_" + shortId() + "@test.com";
  }

  private static String shortId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }
}
