package com.github.fnar.roguelike.dungeon.rooms;

import org.junit.Test;

import greymerk.roguelike.dungeon.base.RoomType;

import static org.assertj.core.api.Assertions.assertThat;

public class DimensionPortalKindTest {

  @Test
  public void from_mapsEachRoomTypeToPackPortalGroup() {
    assertThat(DimensionPortalKind.from(RoomType.NETHER_PORTAL).getRandomPortalsGroupId())
        .isEqualTo("vanilla_nether_portal");
    assertThat(DimensionPortalKind.from(RoomType.ATUM_PORTAL).getRandomPortalsGroupId())
        .isEqualTo("atum_portal");
    assertThat(DimensionPortalKind.from(RoomType.AETHER_PORTAL).getRandomPortalsGroupId())
        .isEqualTo("aether_portal");
    assertThat(DimensionPortalKind.from(RoomType.TWILIGHT_PORTAL).getRandomPortalsGroupId())
        .isEqualTo("twilight_forest_portal");
    assertThat(DimensionPortalKind.from(RoomType.BENEATH_PORTAL).getRandomPortalsGroupId())
        .isEqualTo("beneath_portal");
  }
}
