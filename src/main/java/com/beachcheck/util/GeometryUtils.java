package com.beachcheck.util;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public final class GeometryUtils {

  private static final GeometryFactory GEOMETRY_FACTORY =
      new GeometryFactory(new PrecisionModel(), 4326);

  private GeometryUtils() {}

  public static Point toPoint(double latitude, double longitude) {
    return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
  }

  public static Double extractLatitude(Point point) {
    return point == null ? null : point.getY();
  }

  public static Double extractLongitude(Point point) {
    return point == null ? null : point.getX();
  }
}
