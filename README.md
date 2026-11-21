# Mone

A Baritone-based pathfinding, automation, and HUD minimap mod for **Minecraft 26.2 & 26.3 (Fabric)**.

## Features

### Pathfinding & Automation
- **Smart Pathfinding (`#goto`, `#goal`, `#path`, `#come`, `#thisway`, `#axis`, `#surface`, `#invert`)** — A* pathfinding across terrain with parkour, block breaking/placing, fluid avoidance, and automatic fall-damage handling.
- **Automated Mining (`#mine`, `#tunnel`)** — Locate and mine target blocks or ores (`#mine diamond_ore`), dig straight or custom-sized tunnels (`#tunnel [height width depth]`), with auto-tool switching and unreachable-block blacklisting.
- **Farming (`#farm`)** — Automatically harvest and replant crops.
- **Entity Following & Item Pickup (`#follow`, `#pickup`)** — Follow players or specific mob types, or automatically collect nearby dropped items.
- **World Exploration (`#explore`, `#explorefilter`)** — Automatically explore chunks while caching world data for faster lookups (`#find`).
- **Schematics & Building (`#build`, `#litematica`, `#sel`)** — Build `.schem`, `.schematic`, and `.litematic` files, integrate with Litematica placements, and perform WorldEdit-style region selections (fill, walls, shell, clear, copy/paste).
- **Nether Elytra Flight (`#elytra`)** — Automated long-distance Elytra pathfinding in the Nether powered by `nether-pathfinder`, with automatic firework management and emergency landing.
- **Waypoints (`#wp`, `#sethome`, `#home`)** — Save, manage, and path to named waypoints (including automatic death waypoints).
- **Click-to-Path GUI (`#click`)** — Left-click blocks in the world to path to them, or drag to create a selection.
- **Execution Control & Settings (`#pause`, `#resume`, `#cancel`, `#eta`, `#proc`, `#set`)** — Pause/resume/cancel tasks anytime, check ETA, and customize hundreds of persistent settings.

### HUD Minimap & Fullscreen World Map (`#map` / `#minimap` / `M`)
- **Circular Radar Minimap** — Crisp circular minimap frame with top-down block-colored terrain and distinct highlights for ores (coal, iron, copper, gold, redstone, lapis, diamond, emerald, nether quartz, ancient debris + deepslate variants), water depth, lava, chests, and spawners (toggleable between circular and square via `#map shape`).
- **Interactive Fullscreen World Map (`M` / `#map full`)** — Pan with left-drag, zoom with scroll wheel, left-click anywhere to `#goto`, right-click to save a waypoint, and hover waypoints for details. Keybind is configurable via `#map key <key>` or `#set minimapFullscreenKey <key>`.
- **Waypoints & Death Markers** — Displays Mone's `HOME`, `BED`, `USER`, and `DEATH` waypoints on both the minimap and fullscreen map, clamping off-screen waypoints to the rim as directional indicators.
- **Automatic Layer Switching** — Automatically switches between:
  - **Surface View** outdoors with topographic height relief
  - **Cave Layer View** when underground or in the Nether
  - **Player Y-Layer View** when mining (`#mine` / `#tunnel`)
- **Heading-Up Rotation & Compass** — Smoothly rotates the map and `N`/`S`/`E`/`W` compass as you turn while keeping a static triangle player marker pointing ahead (toggleable via `#map rotate`).
- **Path & Goal Overlay** — Displays your active path, planned next segment, in-progress search path, and goal markers in real time.
- **Unique Mob Head Icons & Elevation Arrows** — Displays vanilla mob faces (60+ mobs) and live player skins with threat-colored borders (red = hostile, green = passive, orange = neutral, cyan = players), upward/downward elevation arrows when mobs are above/below you, and a ±10-block Y filter for non-player entities (players are always shown).
- **Background Worker Thread** — Scans chunk tiles asynchronously on a dedicated worker thread with a per-chunk 16×16 cache and single-chunk dirtying for zero render-thread terrain stutter.

#### `#map` Commands
| Command | Description |
| :--- | :--- |
| `#map` | Toggle the minimap on or off |
| `#map <on\|off\|toggle>` | Explicitly enable, disable, or toggle the minimap |
| `#map full` | Open the interactive fullscreen world map (also bound to `M`) |
| `#map mode <auto\|surface\|cave\|layer>` | Switch between automatic or manual layer modes |
| `#map radius <16-128>` | Set the visible block radius (zoom) |
| `#map size <64-256>` | Set the on-screen minimap size in GUI pixels |
| `#map pos <topright\|topleft\|bottomright\|bottomleft>` | Move the minimap to a different screen corner |
| `#map rotate` | Toggle heading-up rotation vs north-locked |
| `#map shape` | Toggle circular vs square minimap shape |
| `#map waypoints` | Toggle waypoint and death markers |
| `#map key <key>` | Set the fullscreen world map keybind (default: `M`) |
| `#map entities` | Toggle entity & mob head markers |
| `#map path` | Toggle path and goal overlay |

## Requirements
- **Minecraft**: `26.2` or `26.3` (separate release jars are built for each version)
- **Mod Loader**: Fabric Loader `>=0.18.6`
- **Java**: Java 25+ (JDK 26 to build from source)

## Installation
1. Download the `mone-fabric-<version>+mc26.2.jar` or `mone-fabric-<version>+mc26.3.jar` matching your Minecraft version from the [Releases](https://github.com/Skullmc1/Mone/releases) page.
2. Place the `.jar` file into your `.minecraft/mods` folder.
3. Launch Minecraft with Fabric Loader.

## Building from Source
```bash
# Build for Minecraft 26.3 (default)
./gradlew clean :fabric:build -Ptarget_mc=26.3

# Build for Minecraft 26.2
./gradlew clean :fabric:build -Ptarget_mc=26.2
```
The compiled Fabric mod jars are output to `fabric/build/libs/mone-fabric-<version>+mc<mcVersion>.jar`.

## License
Mone is licensed under the [GNU Lesser General Public License v3.0](LICENSE).
