# AGENTS.md

## Project Snapshot
- Fabric mod (`simple-atlas`) for Minecraft `26.2`, Java `25`, Loom `1.17.0-alpha.19`.
- `gradle.properties` currently pins: loader `0.19.3`, Fabric API `0.152.2+26.2`, mod version `1.2.0`, Cloth Config `26.2.155`, ModMenu `20.0.0-beta.2`.
- Main package: `src/main/java/rubbertoe/simple_atlas`.
- Entrypoints in `src/main/resources/fabric.mod.json`: `main`, `client`, `modmenu`, `fabric-datagen`.

## Architecture You Should Learn First
- Server bootstrap (`SimpleAtlas.onInitialize()`) initializes in order: `SimpleAtlasConfigManager.load()`, `ModItems`, `ModMapDecorationTypes`, `ModComponents`, `ModNetworking`, `ModCriteria`, `AtlasViewTicker`.
- In-game configuration is managed by `SimpleAtlasConfigManager` / `SimpleAtlasConfig` (`maxAtlasMapCount`, `maxWaypoints`, `bannerWaypointsOnly`, `waypointIconSize`, `playerIconSize`), with GUI integration via `SimpleAtlasConfigScreen` (Cloth Config) and `SimpleAtlasModMenuIntegration` (ModMenu).
- Atlas state is stored in `ModComponents.ATLAS_CONTENTS` using `component/AtlasContents.java`.
- `AtlasContents` stores map IDs + waypoint state (`waypoints`, `selectedWaypointIconIndex`, `nextWaypointNumber`), `selectedScale`, enforces configured map/waypoint caps dynamically, sanitizes waypoint names/icon indices/dimensions, and retains `blankMapCount` and legacy `sub_map_ids` for codec backwards compatibility.
- Core gameplay logic is in `item/AtlasItem.java`:
  - `useOn` on banners creates banner-derived waypoints with duplicate-position prevention and configured waypoint limit enforcement.
  - `use` groups maps by dimension and scale, builds layout via `AtlasLayoutBuilder.build(...)`, sends `OpenAtlasScreenPayload`, and registers active viewers in `AtlasViewManager`.
  - `inventoryTick` keeps atlas `DataComponents.MAP_ID` synced with current position and selected scale (`AtlasMapSelector`), delegates to `Items.FILLED_MAP.inventoryTick(...)` for vanilla marker behavior, and performs multi-scale live exploration by ticking and updating all overlapping maps covering the player across all scales.
  - `appendHoverText` displays active scale ratio (`1:X`) and lists all contained scale ratios if multiple are present.
- Layout logic (`layout/AtlasLayoutBuilder.java`) computes `AtlasLayout` from same-scale maps using `128 << scale` span and emits per-tile grid positions.
- Cartography behavior is mixin-driven (`CartographyTableMenuMixin`, `CartographyTableAdditionalSlotMixin`, `CartographyTableMapSlotMixin`, `CartographyTableResultSlotMixin`):
  - Flexible slot routing: inputs (Atlas, Book, Filled Map, Paper, Shears) can be placed in either slot `0` or slot `1`.
  - Book + atlas: duplicate atlas.
  - Filled map + atlas: add map after dedupe and configured limits (multi-scale supported).
  - Atlas + paper: scale atlas maps up by +1 in-place using `AtlasCartographyScaler.scaleAtlas`.
  - Atlas + shears: scale atlas maps down by -1 in-place using `AtlasCartographyScaler.downscaleAtlas`. Explored quadrants are split into child maps, and shears take 1 durability damage rather than being consumed.
  - Atlas + atlas: merge map/waypoint contents (multi-scale supported) when total map count does not exceed limit.
- `CartographyTableMenuMixin` also intercepts `quickMoveStack` (shift-click) with intelligent routing for atlas, shears, paper, books, and filled maps, and executes scaling/downscaling upon shift-clicking the result slot. `AbstractContainerMenuInvoker` exposes `moveItemStackTo` and `broadcastChanges`.
- `cartography/AtlasCartographyScaler.java` handles atlas-wide scaling and downscaling:
  - Upscale (+1): uses 1:1 real-world block scanning, heightmap sampling, fluid depth, dithering, modal downsampling, and exploration edge shading.
  - Downscale (-1): generates child quadrant maps for explored areas with parent pixel transfer and real-world surface scanning.
  - Fully integrated with `MapModCompat` to support the `Remapped` mod palette and packet sync.
- `CartographyTableResultSlotMixin` applies server-side post-take effects (book duplication extra copy, shears durability degradation, scale/downscale mutation) and triggers `ModCriteria.ATLAS_CARTOGRAPHY_ACTION`.
- Mod compatibility (`compat/MapModCompat.java`):
  - Detects if `remapped` (`dev.worldgen.remapped`) is loaded via reflection.
  - Synchronizes custom remapped colors, custom block color matching, dithering, and custom network packets during cartography scaling, held map ticking, and atlas viewing.
- Live map sync:
  - `AtlasViewManager` tracks active viewers.
  - `AtlasViewTicker` pushes updates every 10 ticks for active atlas viewers and closes active view if atlas leaves player's hands (sending vanilla and remapped packets).
  - `ServerPlayerMixin` intercepts `synchronizeSpecialItemUpdates` for held atlases.
  - Packet augmentation uses `AtlasWaypointDecorations`.
- Client UI is `client/screen/AtlasScreen.java`:
  - Zoom `0.0625–16.0`, left-drag pan, `R` reset keybind.
  - Multi-scale stepper UI: displays active scale ratio with `<` and `>` buttons and scale toggle to view different map scales.
  - Scale switching preserves the world coordinates of the viewport center and adjusts zoom factor to maintain visual framing.
  - Uses `extractRenderState(...)` rendering flow (not `render`).
  - Dimension tabs are built from `AtlasTilePayload.dimension`, default tab follows `OpenAtlasScreenPayload.playerDimension`, player marker only renders on the player’s current dimension tab.
  - Respects config options for `bannerWaypointsOnly`, `waypointIconSize`, and `playerIconSize`.
  - Waypoint UI supports create/edit/delete, icon cycling, copy coords, teleport command action, and locator-bar pin/unpin.
  - Right-click map context menu supports map removal request (server-authoritative mutation).
- Client visual smoothing mixin: `mixin/client/ItemInHandRendererNoAtlasReequipMixin.java` prevents atlas hand re-equip animation churn on atlas component updates.

## Networking / Data Flow
- Payload classes are in `network/*Payload.java`; registration/receivers are centralized in `network/ModNetworking.java`.
- `AtlasTilePayload` carries `mapId`, `centerX`, `centerZ`, `tileX`, `tileY`, `dimension`, and `scale`.
- `OpenAtlasScreenPayload` carries tiles + atlas map IDs + waypoints + selected icon index + next waypoint number + `playerDimension` + `selectedScale`.
- `CloseAtlasViewPayload` carries `selectedScale` from `AtlasScreen` to persist active viewing scale in the held atlas item.
- Open flow: `AtlasItem.use` -> `OpenAtlasScreenPayload` -> `SimpleAtlasClient` receiver -> `AtlasScreen`.
- Close flow: `AtlasScreen.onClose()` sends `CloseAtlasViewPayload(activeViewScale)`; server updates atlas `selectedScale`, stops viewing, and refreshes held waypoint state.
- Waypoint save flow: `AtlasScreen.persistWaypointState()` -> `SaveAtlasWaypointsPayload` (includes atlas map ID echo) -> server validates atlas identity, sanitizes waypoint list, stores updated `AtlasContents`, reconciles pinned waypoint IDs, and triggers immediate refresh for relevant maps.
- Map removal flow: `AtlasScreen` sends `RemoveAtlasMapPayload` (atlas ID echo + map ID) -> server validates current atlas identity, removes map plus covered waypoints, gives player the removed filled map, reconciles pins, and pushes refresh packets.
- Held-map sync flow: atlas `MAP_ID` selection (`AtlasItem.inventoryTick`) -> `ServerPlayerMixin.synchronizeSpecialItemUpdates` interception -> waypoint decoration augmentation in `AtlasWaypointDecorations`.
- Navigation flow: `NavigateToWaypointPayload` / `UnpinWaypointPayload` / `StopNavigatingPayload` -> server updates `ClientboundTrackedWaypointPacket` pins and performs periodic cleanup when players no longer have an atlas.
- Pin IDs are deterministic from floored waypoint coordinates via `WaypointIconCatalog.navigationWaypointId(...)` (not persisted in `AtlasContents`).

## Developer Workflows
- Build: `./gradlew.bat build`
- Run client: `./gradlew.bat runClient`
- Run dedicated server: `./gradlew.bat runServer`
- Regenerate data assets: `./gradlew.bat runDatagen`
- List tasks: `./gradlew.bat tasks --all`
- Current repo has no `src/test` sources.
- CI reference: `.github/workflows/build.yml` runs `./gradlew build` on Ubuntu `24.04` with Java `25`.

## Agent Tooling Notes
- Use the available `minecraft-dev-*` tools to inspect Minecraft internals (class APIs, packets, registries, mappings) before editing version-sensitive logic.
- Prioritize validating vanilla internals before changing cartography mixins, map packet augmentation, or `AtlasScreen` rendering internals.
- Good first inspection targets: `CartographyTableMenu`, `MapItemSavedData`, map packet types, and rendering APIs used by `AtlasScreen.extractRenderState(...)`.

## Project-Specific Conventions
- Keep registration helpers module-local (`ModItems.register(...)` pattern).
- Use `Identifier.fromNamespaceAndPath(SimpleAtlas.MOD_ID, ...)` for identifiers.
- Atlas map IDs preserve insertion order + dedupe (`LinkedHashSet`).
- Keep waypoint limits aligned: name length `32`, server waypoint cap dynamically capped via config (`SimpleAtlasConfigManager.getMaxWaypoints()`).
- Keep waypoint icon sets in sync across:
  - `navigation/WaypointIconCatalog.java`
  - `assets/simple-atlas/textures/gui/icons/*.png`
  - `assets/simple-atlas/waypoint_style/*.json`
- Treat `src/main/generated` as datagen output; edit providers under `datagen/*Provider.java` instead of generated JSON.
- Keep mixin helper prefixes as `simple_atlas$...`.
- Keep client/server responsibilities separated (`client/*` vs `server/*`).

## High-Risk Integration Points
- `CartographyTableMenu` internals and inner-slot mixin targets (`$3/$4/$5`) are version-sensitive.
- Map sync depends on `MapItemSavedData#getUpdatePacket`; maintain null-safe behavior and manual refresh fallback semantics where already used.
- `AtlasWaypointDecorations` must preserve vanilla empty-decoration behavior; forcing empty decoration payloads causes flicker/marker clears.
- Multi-scale atlas cartography logic and layout builders require handling multiple scales cleanly (`AtlasItem`, `AtlasLayoutBuilder`, `CartographyTableMenuMixin`, `AtlasCartographyScaler`).
- `AtlasCartographyScaler` depends on `MapItemSavedData.scaled()`, `createFresh(...)`, `setColor(...)`, heightmaps, and world surface chunk blocks; re-check projection/dedupe/edge-shading behavior after MC updates.
- `ServerPlayerMixin` injection target (`ServerPlayer#synchronizeSpecialItemUpdates`) must be re-validated on updates.
- Locator-bar pin cleanup relies on packet updates plus server-side reconciliation in `ModNetworking` (save/removal/inventory-check/disconnect paths).
- Dimension tab correctness depends on `AtlasTilePayload.dimension` and `OpenAtlasScreenPayload.playerDimension`; mismatches break tab selection and player-marker visibility.
- `AtlasScreen.extractRenderState(...)` and related rendering APIs are version-sensitive and should be rechecked after MC/Fabric updates.

