package com.beachcheck.fixture;

import com.beachcheck.domain.Beach;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

public final class BeachTestFixtures {

  private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

  private BeachTestFixtures() {}

  public static Beach createBeach(UUID id) {
    return createBeach(id, "해운대");
  }

  public static Beach createBeach(UUID id, String name) {
    Beach beach = new Beach();
    beach.setId(id);
    beach.setName(name);
    beach.setStatus("OPEN");
    return beach;
  }

  public static Beach createBeachWithLocation(String code, String name, double lon, double lat) {
    Beach beach = new Beach();
    beach.setCode(code);
    beach.setName(name);
    beach.setStatus("OPEN");

    Point location = GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
    location.setSRID(4326);
    beach.setLocation(location);

    return beach;
  }
}
