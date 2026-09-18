package greymerk.roguelike.worldgen;

import greymerk.roguelike.worldgen.shapes.IShape;
import greymerk.roguelike.worldgen.shapes.Shape;

public interface Bounded {

  default boolean collide(Bounded other) {
    if (getEnd().getX() < other.getStart().getX()
        || other.getEnd().getX() < getStart().getX()) {
      return false;
    }

    if (getEnd().getY() < other.getStart().getY()
        || other.getEnd().getY() < getStart().getY()) {
      return false;
    }

    return getEnd().getZ() >= other.getStart().getZ()
        && other.getEnd().getZ() >= getStart().getZ();
  }

  IShape getShape(Shape type);

  Coord getStart();

  Coord getEnd();

  default boolean containsCoord(Coord coord) {
    Coord a = getStart();
    Coord b = getEnd();
    int minX = Math.min(a.getX(), b.getX());
    int maxX = Math.max(a.getX(), b.getX());
    int minY = Math.min(a.getY(), b.getY());
    int maxY = Math.max(a.getY(), b.getY());
    int minZ = Math.min(a.getZ(), b.getZ());
    int maxZ = Math.max(a.getZ(), b.getZ());
    return coord.getX() >= minX && coord.getX() <= maxX
        && coord.getY() >= minY && coord.getY() <= maxY
        && coord.getZ() >= minZ && coord.getZ() <= maxZ;
  }

}
