# Simple Atlas

Carry your mapped world in one item.

Simple Atlas adds an **Atlas** item that stores multiple filled maps and opens into a single interactive view, so you can pan around your explored area without juggling lots of maps.

## Features

### Atlas item & Cartography Table

- **Craft an Atlas** and add filled maps through the cartography table.
- **Flexible Cartography Table Routing:** Ingredients can be placed in either the primary (top) or secondary (bottom) slot.
- **Scale Up (Paper):** Combine an Atlas with paper to scale up all maps by one level (+1). The process employs 1:1 real-world block scanning, high-fidelity modal downsampling, and edge shading.
- **Scale Down (Shears):** Combine an Atlas with shears to downscale maps by one level (-1). Each explored quadrant is split into child maps, and the shears consume 1 durability point rather than being destroyed.
- **Duplicate (Book):** Combine an Atlas with a book at the cartography table to produce an identical duplicate copy.
- **Merge Atlases:** Combine two atlases to merge their maps and waypoints into one atlas (multi-scale supported, up to the configured map limit).
- **Multi-Scale Atlas Support & Tooltips:** An atlas can store maps across multiple scales. Hovering over an atlas displays the currently active scale (`Scale: 1:X`) as well as all available scales contained inside.

### Interactive World Map

- **Scroll to Zoom:** Extended zoom range from `0.0625x` (1/16x) up to `16.0x`.
- **Left-Drag to Pan:** Smooth panning across your explored regions.
- **Multi-Scale Stepper UI:** Seamlessly switch between stored map scales (`1:1`, `1:2`, `1:4`, etc.) using on-screen stepper buttons (`<` / `>`) or by clicking the scale indicator.
- **Viewport Preservation:** Switching scales preserves the world-coordinate center of the viewport and automatically compensates the zoom factor so your visual framing remains stable.
- **Player Marker & Reset Keybind:** Player position and orientation are shown directly on the atlas; press `R` (configurable) to immediately center and reset zoom to your player position.
- **Dimension Bookmark Tabs:** Dedicated bookmark tabs for Overworld, Nether, End, and custom dimensions.

### Waypoints

- **Create Waypoints:** Right-click on the atlas to place new custom waypoints.
- **Banner Waypoints:** Use an atlas on a placed banner in-world to create a waypoint matching the banner's name and color.
- **Context Actions:** Right-click existing waypoints to edit, change icons, delete, copy coordinates, or teleport (if server permissions allow).
- **Icon Selector:** Built-in catalog of custom icons (settlements, points of interest, ores, markers, and colored banners).
- **Map Removal:** Right-click a mapped tile in the atlas to remove the map from the atlas and return the filled map item to your inventory.

### Navigation Compass

- Choose **Locate** on any waypoint to pin it directly to your HUD locator compass bar.
- Multiple waypoints can be pinned simultaneously.
- Choose **Stop Locating** to remove the pin.

### Multi-Scale Live Exploration & Sync

- **Simultaneous Multi-Scale Exploration:** While carrying an atlas, player exploration simultaneously explores and updates all overlapping maps across all stored scales in the atlas.
- **Live Sync:** Atlas map data is synced live from the server to active viewers.
- **Remapped Mod Compatibility:** Built-in compatibility with the [Remapped](https://modrinth.com/mod/remapped) mod (`dev.worldgen.remapped`), preserving high-fidelity block palette colors, dithering, and custom network packets during exploration and cartography operations.

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
2. Insert filled maps into the Atlas using the Cartography Table.
3. In the Cartography Table, you can also:
   - Scale up using **Paper**
   - Scale down using **Shears**
   - Duplicate using a **Book**
   - Merge another Atlas (multi-scale supported)
4. Hold the Atlas in either hand and use (right-click) it to open the interactive screen.
5. Zoom, pan, switch map scales, and browse dimensions using bookmark tabs.
6. Right-click map locations to create waypoints, copy coordinates, or remove individual maps.
7. Locate waypoints on your HUD locator bar for effortless navigation.

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
- **Remapped:** Optional mod compatibility

## Notes

- Waypoint, scale, and navigation state are persisted directly in the atlas item components.
- Teleport actions in the atlas UI use standard player commands and respect server permission levels.
- Designed for both singleplayer and multiplayer survival gameplay.

## Issues/Feedback

- Report bugs or suggest improvements on the [GitHub Issues](https://github.com/RubberToe-06/simple_atlas/issues) page.
