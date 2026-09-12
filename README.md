# Simple Atlas

Carry your mapped world in one item.

Simple Atlas adds an **Atlas** item that stores multiple filled maps and opens into a single interactive view, so you can pan around your explored area without juggling lots of maps.

## Features

### Atlas item & Cartography Table

- **Craft an Atlas** and add filled maps or empty maps through the cartography table or directly via crafting grids.
- **Crafting Grid Map Insertion:** Combine an Atlas with filled maps or empty maps directly in the 2x2 player crafting inventory or a 3x3 crafting table to insert them quickly on the go.
- **Empty Map Storage & Seamless Auto-Mapping:** Store blank maps inside the Atlas. When you walk outside your explored map boundaries while carrying the Atlas, it automatically consumes a stored blank map, creates the new grid-aligned map tile, and continues recording your adventure seamlessly.
- **Instant First Map Auto-Creation:** Opening an Atlas that contains stored blank maps (even with 0 filled maps) automatically generates and centers your starting map.
- **Main-Hand & Off-Hand Usability:** Carry and use an Atlas in either hand, with full interactive screen support, live exploration, and syncing.
- **Flexible Cartography Table Routing:** Ingredients can be placed in either the primary (top) or secondary (bottom) slot, with intelligent shift-click (Quick Move) routing.
- **Scale Up (Paper):** Combine an Atlas with paper to scale up all maps by one level (+1). The process employs 1:1 real-world block scanning and high-fidelity modal downsampling, preserving explored boundaries without destructive edge erosion.
- **Scale Down (Shears):** Combine an Atlas with shears to downscale maps by one level (-1). Each explored quadrant is split into child maps, and the shears consume 1 durability point rather than being destroyed.
- **Duplicate (Book):** Combine an Atlas with a book at the cartography table to produce an identical duplicate copy.
- **Merge Atlases:** Combine two atlases to merge their maps, waypoints, and stored blank maps into one atlas (multi-scale supported, up to the configured limits).
- **Multi-Scale Atlas Support & Tooltips:** An atlas can store maps across multiple scales. Hovering over an atlas displays the currently active scale (`Scale: 1:X`), all available scales contained inside, and the number of stored empty maps available for automatic exploration.

### Interactive World Map

- **Scroll to Zoom:** Extended zoom range from `0.0625x` (1/16x) up to `16.0x`.
- **Left-Drag to Pan:** Smooth panning across your explored regions.
- **Multi-Scale Stepper UI:** Seamlessly switch between stored map scales (`1:1`, `1:2`, `1:4`, etc.) using on-screen stepper buttons (`<` / `>`) or by clicking the scale indicator.
- **Viewport Preservation:** Switching scales preserves the world-coordinate center of the viewport and automatically compensates the zoom factor so your visual framing remains stable.
- **Player Marker & Reset Keybind:** Player position and orientation are shown directly on the atlas; press `R` (configurable) to immediately center and reset zoom to your player position.
- **Dimension Bookmark Tabs:** Dedicated bookmark tabs for Overworld, Nether, End, and custom dimensions.

### Waypoints

- **Create Waypoints:** Right-click on the atlas to place new custom waypoints.
- **In-Map Waypoint Labels:** Waypoints display their custom names directly beneath their icons on the atlas with clean, proportional typography (`0.5x` scale matching vanilla map text) that stays sharp, legible, and uncrowded at any zoom level.
- **Banner Waypoints:** Use an atlas on a placed banner in-world to create a waypoint matching the banner's name and color.
- **Context Actions:** Right-click existing waypoints to edit, change icons, delete, copy coordinates, or teleport (if server permissions allow).
- **Icon Selector:** Built-in catalog of custom icons (settlements, points of interest, ores, markers, and colored banners).
- **Map Removal:** Right-click a mapped tile in the atlas to remove the map from the atlas and return the filled map item to your inventory without closing the screen (remaining tiles and waypoints update dynamically in real time).

### Navigation Compass

- Choose **Locate** on any waypoint to pin it directly to your HUD locator compass bar.
- Multiple waypoints can be pinned simultaneously.
- Choose **Stop Locating** to remove the pin.

### Multi-Scale Live Exploration & Mod Compatibility

- **Simultaneous Multi-Scale Exploration:** While carrying an atlas, player exploration simultaneously explores and updates all overlapping maps across all stored scales in the atlas.
- **Live Sync:** Atlas map data is synced live from the server to active viewers.
- **Remapped Mod Compatibility:** Built-in compatibility with the [Remapped](https://modrinth.com/mod/remapped) mod (`dev.worldgen.remapped`), preserving high-fidelity 16-bit block palette colors, dithering, empty map types, and custom network packets during live exploration and cartography operations.
- **Immersive Overlays Mod Compatibility:** Automatic integration with [Immersive Overlays](https://modrinth.com/mod/immersiveoverlays) — displays biome overlays when carrying an Atlas and ensures smooth waypoint navigation on the compass bar.

### Configuration

Simple Atlas includes an in-game configuration screen powered by **Cloth Config** and **ModMenu**:
- **Max maps per atlas:** Maximum number of maps allowed in a single atlas (default: 256, range: 1–256).
- **Max waypoints per atlas:** Maximum number of waypoints allowed (default: 256, range: 1–256).
- **Banner waypoints only:** Option to restrict waypoint creation strictly to in-world banners.
- **Waypoint icon size:** Adjust the render scale of waypoint icons on the atlas (range: 0.5x to 2.0x).
- **Player icon size:** Adjust the render scale of the player position marker (range: 0.5x to 2.0x).

### Advancements

Includes custom advancements under the **Adventure** tab:
- **Paper Trail:** Obtain an Atlas.
- **Old Fashioned:** Add a waypoint to an Atlas using a banner.
- **Backup Copy:** Duplicate an Atlas with a book at a cartography table.
- **Better Together:** Merge two Atlases at a cartography table.
- **Bigger Picture:** Upscale an Atlas with paper at a cartography table.
- **Marco!:** Pin a waypoint to the locator bar.

## How to use

1. Craft an Atlas (shapeless: book + filled map, or as configured).
2. Insert filled maps or blank maps into the Atlas using the Cartography Table or any Crafting Grid (including the 2x2 player inventory grid).
3. In the Cartography Table, you can also:
   - Scale up using **Paper**
   - Scale down using **Shears**
   - Duplicate using a **Book**
   - Merge another Atlas (multi-scale supported)
4. Hold the Atlas in either hand and use (right-click) it to open the interactive screen.
5. While adventuring, carrying an Atlas with stored blank maps will automatically generate and map new tiles as you travel!
6. Zoom, pan, switch map scales, and browse dimensions using bookmark tabs.
7. Right-click map locations to create waypoints, copy coordinates, or remove individual maps.
8. Locate waypoints on your HUD locator bar for effortless navigation.

## Recipe

![Atlas crafting recipe](https://cdn.modrinth.com/data/cached_images/680b305403d8fe874f9c1d265f99f98b5e1a3cea.png)
- Shapeless crafting recipe

## Compatibility & Requirements

- **Minecraft:** `26.2`
- **Loader:** Fabric (`fabric-loader >= 0.19.3`)
- **Java:** `25+`
- **Fabric API:** Required (`>= 0.152.2+26.2`)
- **Cloth Config:** Required (`>= 26.2.155`)
- **ModMenu:** Optional / Recommended (`>= 20.0.0-beta.2`)
- **Remapped:** Optional mod compatibility (`dev.worldgen.remapped`)
- **Immersive Overlays:** Optional mod compatibility (`cc.cassian.immersiveoverlays`)

## Notes

- Waypoint, scale, and navigation state are persisted directly in the atlas item components.
- Teleport actions in the atlas UI use standard player commands and respect server permission levels.
- Designed for both singleplayer and multiplayer survival gameplay.

## Issues/Feedback

- Report bugs or suggest improvements on the [GitHub Issues](https://github.com/RubberToe-06/simple_atlas/issues) page.
