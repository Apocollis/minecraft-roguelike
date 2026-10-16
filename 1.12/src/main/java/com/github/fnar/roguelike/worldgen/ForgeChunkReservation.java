package com.github.fnar.roguelike.worldgen;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

import com.github.fnar.roguelike.Roguelike;

import greymerk.roguelike.dungeon.Dungeon;
import greymerk.roguelike.mixin.MixinChunkPopulateInvoker;
import greymerk.roguelike.worldgen.ChunkLoadState;
import greymerk.roguelike.worldgen.ChunkReservation;

/**
 * Holds a dungeon's footprint chunks with Forge tickets while its build job runs.
 *
 * <p>Each chunk is forced right after it is loaded, never before: Forge loads (and generates)
 * every forced chunk on each world tick, so forcing an ungenerated footprint up front would
 * generate all of it in one tick.
 */
public class ForgeChunkReservation implements ChunkReservation {

  private static final Logger logger = LogManager.getLogger(Dungeon.MOD_ID);

  private final World world;
  private final List<Ticket> tickets = new ArrayList<>();
  private final Set<ChunkPos> held = new HashSet<>();

  public ForgeChunkReservation(World world, int chunkCount) {
    this.world = world;
    if (Roguelike.instance == null || chunkCount <= 0) {
      return;
    }
    int perTicket = Math.max(1, ForgeChunkManager.getMaxChunkDepthFor(Dungeon.MOD_ID));
    int needed = (chunkCount + perTicket - 1) / perTicket;
    for (int i = 0; i < needed; i++) {
      Ticket ticket = ForgeChunkManager.requestTicket(Roguelike.instance, world, ForgeChunkManager.Type.NORMAL);
      if (ticket == null) {
        logger.warn("Forge refused chunk ticket {}/{}; {} footprint chunks may unload between build steps and be reloaded.",
            i + 1, needed, chunkCount - i * perTicket);
        break;
      }
      tickets.add(ticket);
    }
  }

  @Override
  public void loadAndHold(int chunkX, int chunkZ) {
    world.getChunkFromChunkCoords(chunkX, chunkZ);
    hold(chunkX, chunkZ);
  }

  /**
   * Plain lookup in the loaded-chunk map. Never {@code getLoadedChunk}: it clears
   * {@code unloadQueued}, cancelling a pending unload, and the prepare policy polls every tick,
   * including chunks outside the footprint.
   */
  private Chunk peek(int chunkX, int chunkZ) {
    if (!(world instanceof WorldServer)) {
      return null;
    }
    return ((WorldServer) world).getChunkProvider().id2ChunkMap.get(ChunkPos.asLong(chunkX, chunkZ));
  }

  @Override
  public boolean isLoaded(int chunkX, int chunkZ) {
    return peek(chunkX, chunkZ) != null;
  }

  @Override
  public ChunkLoadState stateOf(int chunkX, int chunkZ) {
    Chunk chunk = peek(chunkX, chunkZ);
    if (chunk != null) {
      return chunk.isTerrainPopulated() ? ChunkLoadState.LOADED_POPULATED : ChunkLoadState.LOADED_UNPOPULATED;
    }
    if (world instanceof WorldServer && ((WorldServer) world).getChunkProvider().isChunkGeneratedAt(chunkX, chunkZ)) {
      return ChunkLoadState.ON_DISK;
    }
    return ChunkLoadState.UNGENERATED;
  }

  /**
   * Forces an already loaded chunk on our ticket. Only call for a loaded chunk, or right before
   * loading it in the same call: Forge loads (and generates) every forced chunk on the next world
   * tick, so forcing ungenerated chunks ahead of time would generate them all at once.
   */
  @Override
  public void hold(int chunkX, int chunkZ) {
    ChunkPos pos = new ChunkPos(chunkX, chunkZ);
    if (held.contains(pos)) {
      return;
    }
    Ticket ticket = ticketWithRoom();
    if (ticket != null) {
      ForgeChunkManager.forceChunk(ticket, pos);
      held.add(pos);
    }
  }

  /**
   * Loads through the normal path ({@code getChunkFromChunkCoords}) so Forge, Depths Update and
   * lazychunks hooks all run, while {@link PopulateSuppress} cancels every populate the load starts.
   * Forced first, right before the load, so lazychunks' ticket exemption and Forge both see it.
   */
  @Override
  public void loadSuppressed(int chunkX, int chunkZ) {
    hold(chunkX, chunkZ);
    PopulateSuppress.begin();
    try {
      world.getChunkFromChunkCoords(chunkX, chunkZ);
    } finally {
      PopulateSuppress.end();
    }
  }

  @Override
  public void populateAndHold(int chunkX, int chunkZ) {
    hold(chunkX, chunkZ);
    populateOne(chunkX, chunkZ);
  }

  /**
   * Populates only this chunk, never its neighbours (the two-arg populate would do up to four).
   * Vanilla's own precondition, checked by the caller: its +X, +Z and +XZ neighbours are loaded.
   */
  @Override
  public void populateOne(int chunkX, int chunkZ) {
    Chunk chunk = peek(chunkX, chunkZ);
    if (chunk == null || chunk.isTerrainPopulated() || !(world instanceof WorldServer)) {
      return;
    }
    ((MixinChunkPopulateInvoker) chunk).roguelike$populate(((WorldServer) world).getChunkProvider().chunkGenerator);
  }

  @Override
  public List<Long> drainSuppressed() {
    return PopulateSuppress.drain();
  }

  @Override
  public void release() {
    for (Ticket ticket : tickets) {
      ForgeChunkManager.releaseTicket(ticket);
    }
    tickets.clear();
    held.clear();
  }

  private Ticket ticketWithRoom() {
    for (Ticket ticket : tickets) {
      if (ticket.getChunkList().size() < ticket.getMaxChunkListDepth()) {
        return ticket;
      }
    }
    return null;
  }

  /**
   * Build jobs do not survive a restart, but Forge saves their tickets. Release every ticket
   * handed back on world load so those chunks do not stay loaded forever.
   */
  public static class OrphanedTicketReleaser implements ForgeChunkManager.LoadingCallback {

    @Override
    public void ticketsLoaded(List<Ticket> loaded, World world) {
      for (Ticket ticket : loaded) {
        ForgeChunkManager.releaseTicket(ticket);
      }
      logger.info("Released {} orphaned dungeon chunk ticket(s) in dimension {}.",
          loaded.size(), world.provider.getDimension());
    }
  }
}
