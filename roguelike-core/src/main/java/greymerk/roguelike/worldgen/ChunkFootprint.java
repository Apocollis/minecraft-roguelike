package greymerk.roguelike.worldgen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The chunks a dungeon build needs, split by what each must be before blocks are written.
 *
 * <ul>
 *   <li><b>Inner</b>: chunks the dungeon writes into (layout boxes, tower area).</li>
 *   <li><b>Populate set</b>: inner, plus every other footprint chunk with an inner chunk at its
 *   +X, +Z or +XZ. A chunk's populate decorates the 16x16 area offset +8 blocks, which reaches
 *   those neighbours, so these must be populated before the build or a later populate would put
 *   trees, lakes and ores over dungeon blocks.</li>
 *   <li><b>Load-only</b>: the rest (the +X/+Z rim and outer corners). Loaded and held so the
 *   populate set can populate, but never waited on: the rim's own +X/+Z neighbours are outside the
 *   footprint, so it could never populate.</li>
 * </ul>
 *
 * Every populate-set chunk's +X, +Z and +XZ neighbours are in the footprint, as long as every inner
 * chunk's eight neighbours are (the footprint is inner plus a one-chunk margin).
 */
public final class ChunkFootprint {

  private final List<Long> all;
  private final Set<Long> inner;
  private final Set<Long> populate;

  public ChunkFootprint(Collection<Long> allChunks, Collection<Long> innerChunks) {
    Set<Long> allSet = new LinkedHashSet<>(allChunks);
    allSet.addAll(innerChunks);
    this.all = Collections.unmodifiableList(new ArrayList<>(allSet));
    this.inner = Collections.unmodifiableSet(new LinkedHashSet<>(innerChunks));
    this.populate = Collections.unmodifiableSet(populateSet(this.inner, allSet));
  }

  static Set<Long> populateSet(Set<Long> inner, Set<Long> all) {
    Set<Long> populate = new LinkedHashSet<>(inner);
    for (long chunk : all) {
      int x = ChunkArea.chunkX(chunk);
      int z = ChunkArea.chunkZ(chunk);
      if (inner.contains(ChunkArea.pack(x + 1, z))
          || inner.contains(ChunkArea.pack(x, z + 1))
          || inner.contains(ChunkArea.pack(x + 1, z + 1))) {
        populate.add(chunk);
      }
    }
    return populate;
  }

  /** Every footprint chunk, inner first-seen order preserved. */
  public List<Long> getAll() {
    return all;
  }

  public Set<Long> getInner() {
    return inner;
  }

  public Set<Long> getPopulateSet() {
    return populate;
  }

  public boolean mustPopulate(long chunk) {
    return populate.contains(chunk);
  }
}
