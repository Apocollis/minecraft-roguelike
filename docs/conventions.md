# Conventions

## Plan before code

Repo rule (`.cursor/rules/plan-before-changes.mdc`): for features, refactors, and non-trivial fixes, **propose a plan and wait for explicit approval** before editing. Questions and one-line clarifications do not need a plan. Crash regressions of a change just shipped may be fixed immediately when the cause is unambiguous.

## Permissions (locked)

| Command | Level |
|---------|--------|
| `/roguelike` entire tree | **4** |
| `/whereami` | **0** |
| Vanilla `/locate` | **2** |

Do not split `/roguelike locate` down to 2. Vanilla `/locate RoguelikeDungeon` is the perm-2 locate.

## New code placement

- Dungeon/room/theme logic → `roguelike-core` unless it needs Minecraft types.
- Forge/mappers/mixins → `1.12`.
- Prefer existing packages over a third tree. New rooms may live next to `prototype` or `com.github.fnar.roguelike.dungeon.rooms`; `RoomSetting` must register them.

## Mixins

Vanilla targets: SRG + `remap = false`. Helpers: `private`. Never mixin `World` in the current mixin config. See [mixins.md](mixins.md).

## Pack context

This fork is built for **Arcana Quest DEVBOX**: Forge 1.12.2 + Cleanroom Relauncher + Tweaks. Gameplay assumptions (Rustic lanterns, Y −64, village vs temple distances) are pack-driven. Code defaults may differ from the Devbox `roguelike.cfg`.
