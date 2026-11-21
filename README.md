# Mone

A Baritone-based pathfinding, automation, survival safety, and HUD minimap mod for **Minecraft 26.2 & 26.3 (Fabric)**.

## Features

### Pathfinding & Automation
- **Smart Pathfinding (`#goto`, `#goal`, `#path`, `#come`, `#thisway`, `#axis`, `#surface`, `#invert`)** — A* pathfinding across terrain with parkour, block breaking/placing, fluid avoidance, and automatic fall-damage handling.
- **Automated Mining (`#mine`, `#tunnel`)** — Locate and mine target blocks or ores (`#mine diamond_ore`), dig straight or custom-sized tunnels (`#tunnel [height width depth]`), with auto-tool switching and unreachable-block blacklisting.
- **Task Queue / Macro Chains (`#queue`, `#q`)** — Chain multiple commands sequentially with semicolons (e.g., `#queue mine 64 iron_ore; home; farm`), automatically starting each task once the previous one finishes.
- **Smart Auto-Drop-Off (`#dropoff`)** — Automatically interrupts `#mine` or `#farm` when inventory fills up, paths to your designated storage waypoint, opens a nearby chest/barrel, deposits non-essential loot (keeping tools, weapons, food, torches, buckets, totems, and fireworks), and returns to resume.
- **Farming (`#farm`)** — Automatically harvest and replant crops.
- **Entity Following & Item Pickup (`#follow`, `#pickup`)** — Follow players or specific mob types, or automatically collect nearby dropped items.
- **World Exploration (`#explore`, `#explorefilter`)** — Automatically explore chunks while caching world data for faster lookups (`#find`).
- **Schematics & Building (`#build`, `#litematica`, `#sel`)** — Build `.schem`, `.schematic`, and `.litematic` files, integrate with Litematica placements, and perform WorldEdit-style region selections (fill, walls, shell, clear, copy/paste).
- **Nether Elytra Flight (`#elytra`)** — Automated long-distance Elytra pathfinding in the Nether powered by `nether-pathfinder`, with automatic firework management and emergency landing.
- **Waypoints (`#wp`, `#sethome`, `#home`)** — Save, manage, and path to named waypoints (including automatic death waypoints).
- **Click-to-Path GUI (`#click`)** — Left-click blocks in the world to path to them, or drag to create a selection.
- **Execution Control & Settings (`#pause`, `#resume`, `#cancel`, `#eta`, `#proc`, `#set`)** — Pause/resume/cancel tasks anytime, check ETA, and customize hundreds of persistent settings.

### Survival Safety & Auto-Eat
- **Auto-Eat (`autoEat`)** — Automatically pauses pathing to eat the best safe food from your hotbar or inventory (avoiding rotten flesh, spider eyes, pufferfish, etc.) when hunger or health drops below configured thresholds, then restores your slot and resumes.
- **Dynamic Enemy Mob Evasion (`survivalSafety`, `evadeEnemyMobs`)** — Continuously recalculates a `GoalRunAway` path to evade nearby hostile mobs until reaching a safe distance (ignoring passive Endermen and un-aggroed Zombified Piglins), and triggers emergency retreat at low health.
- **Low-Durability Tool Swap (`swapLowDurabilityTool`)** — Automatically swaps in a healthier backup tool of the same type from inventory before your held tool breaks.

### Active-Work HUD Overlay (`#hud`)
- Toggle via `#hud` (`#hud on` / `#hud off`).
- Stays invisible when idle; whenever a Mone process is running, renders a pulsing cyan gradient border fading toward the center around all four screen edges plus a top-center status banner (`Mone is working. (<active task>)`).

### In-Game Settings GUI (`#gui` / `K`)
- Press `K` in-game (configurable via `settingsGuiKey`) or run `#gui` to open a searchable, categorized settings UI (`Quick`, `Minimap`, `Safety`, `Pathing`, `All`) with toggle pills, `-`/`+` number controls, inline text editing, per-setting reset (`R`), and a **Save Config** button.

### HUD Minimap & Fullscreen World Map (`#map` / `#minimap` / `M`)
- **Circular Radar Minimap** — Crisp circular minimap frame with top-down biome-tinted terrain and distinct highlights for ores (coal, iron, copper, gold, redstone, lapis, diamond, emerald, nether quartz, ancient debris + deepslate variants), water depth, lava, chests, and spawners (toggleable between circular and square via `#map shape`).
- **Persistent Disk World Map** — Explored surface chunks are saved to GZIP-compressed 16×16 region files (`Mone/map_cache/<world>/<dimension>/`) so your fullscreen map remembers explored terrain across sessions.
- **Interactive Fullscreen World Map (`M` / `#map full`)** — Left-drag to pan, scroll to zoom, left-click to `#goto`, **Ctrl + Left-Drag** to create a `#sel` selection, right-click empty space to open the **Name Waypoint** dialog with 9 custom color swatches, and right-click any waypoint for a `Goto` / `Rename` / `Delete` menu.
- **Waypoints Search Sidebar (`W`)** — Searchable sidebar in the fullscreen map to filter waypoints by name or tag (e.g., `death`), click to pan, or quick-`Go` / delete.
- **Nether ↔ Overworld Linked Coordinates** — Displays linked Nether (`÷8`) or Overworld (`×8`) coordinates in the fullscreen cursor header and waypoint tooltips.
- **Automatic Layer Switching** — Automatically switches between **Surface** (with biome-tinted foliage/water & topographic relief), **Cave Layer** (underground / Nether), and **Player Y-Layer** (while mining with `#mine` / `#tunnel`).
- **Heading-Up Rotation & Compass** — Smoothly rotates the map and `N`/`S`/`E`/`W` compass as you turn while keeping a static triangle player marker pointing ahead (toggleable via `#map rotate`).
- **Chunk Grid (`G` / `#map chunks`) & Mob-Spawn Overlay (`L` / `#map spawns`)** — Toggle 16×16 chunk borders and mob-spawnable dark block highlights (yellow = night, red = always).
- **Path & Goal Overlay** — Displays active path, planned next segment, in-progress search path, and goal markers in real time.
- **Unique Mob Head Icons & Elevation Arrows** — Displays vanilla mob faces (60+ mobs) and live player skins with threat-colored borders, upward/downward elevation arrows, and a ±10-block Y filter for non-player entities (players are always shown).
- **Background Worker Thread** — Scans chunk tiles and disk cache asynchronously on a dedicated daemon worker thread for smooth FPS.

#### Command Quick Reference
| Command | Description |
| :--- | :--- |
| `#map` / `#map <on\|off\|toggle>` | Toggle or set the HUD minimap |
| `#map full` (or `M`) | Open the interactive fullscreen world map |
| `#map mode <auto\|surface\|cave\|layer>` | Switch between automatic or manual layer modes |
| `#map radius <16-128>` / `#map size <64-256>` | Adjust zoom radius or on-screen size |
| `#map pos <topright\|topleft\|bottomright\|bottomleft>` | Move the minimap corner |
| `#map rotate` / `#map shape` | Toggle heading-up rotation or circular/square shape |
| `#map chunks` / `#map spawns` | Toggle chunk border grid or mob-spawn light overlay |
| `#map waypoints` / `#map entities` / `#map path` | Toggle waypoint, entity, or path overlays |
| `#map key <key>` | Set the fullscreen world map keybind (default: `M`) |
| `#hud` / `#hud <on\|off\|toggle>` | Toggle the active-work screen border & status banner |
| `#gui` (or `K`) | Open the searchable in-game Settings GUI |
| `#queue <cmd1; cmd2; ...>` | Queue semicolon-separated commands sequentially |
| `#queue <add\|list\|skip\|pause\|resume\|clear>` | Manage the command queue |
| `#dropoff` / `#dropoff <set\|now\|waypoint <name>>` | Toggle, configure, or trigger Smart Auto-Drop-Off |

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
