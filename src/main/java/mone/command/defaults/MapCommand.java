/*
 * This file is part of Mone.
 *
 * Mone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Mone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Mone.  If not, see <https://www.gnu.org/licenses/>.
 */

package mone.command.defaults;

import mone.api.IMone;
import mone.api.MoneAPI;
import mone.api.Settings;
import mone.api.command.Command;
import mone.api.command.argument.IArgConsumer;
import mone.api.command.exception.CommandException;
import mone.api.command.exception.CommandInvalidStateException;
import mone.api.command.helpers.TabCompleteHelper;
import mone.utils.MinimapRenderer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class MapCommand extends Command {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "on", "off", "toggle", "full", "mode", "radius", "size", "pos", "rotate", "shape", "waypoints", "entities", "path", "key"
    );
    private static final List<String> MODES = Arrays.asList("auto", "surface", "cave", "layer");
    private static final List<String> POSITIONS = Arrays.asList("topright", "topleft", "bottomright", "bottomleft");

    public MapCommand(IMone mone) {
        super(mone, "map", "minimap");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        Settings settings = MoneAPI.getSettings();

        if (!args.hasAny()) {
            settings.minimap.value = !settings.minimap.value;
            MinimapRenderer.invalidate();
            logDirect("Minimap " + (settings.minimap.value ? "enabled" : "disabled"));
            return;
        }

        String sub = args.getString().toLowerCase(Locale.ROOT);
        switch (sub) {
            case "on", "enable", "show" -> {
                args.requireMax(0);
                settings.minimap.value = true;
                MinimapRenderer.invalidate();
                logDirect("Minimap enabled");
            }
            case "off", "disable", "hide" -> {
                args.requireMax(0);
                settings.minimap.value = false;
                logDirect("Minimap disabled");
            }
            case "toggle" -> {
                args.requireMax(0);
                settings.minimap.value = !settings.minimap.value;
                MinimapRenderer.invalidate();
                logDirect("Minimap " + (settings.minimap.value ? "enabled" : "disabled"));
            }
            case "mode" -> {
                if (!args.hasAny()) {
                    logDirect("Current minimap mode: " + settings.minimapMode.value);
                    return;
                }
                args.requireMax(1);
                String mode = args.getString().toLowerCase(Locale.ROOT);
                if (!MODES.contains(mode)) {
                    throw new CommandInvalidStateException("Invalid mode '" + mode + "'. Expected one of: " + String.join(", ", MODES));
                }
                settings.minimapMode.value = mode;
                MinimapRenderer.invalidate();
                logDirect("Minimap mode set to " + mode);
            }
            case "radius", "zoom" -> {
                if (!args.hasAny()) {
                    logDirect("Current minimap radius: " + settings.minimapRadius.value + " blocks");
                    return;
                }
                args.requireMax(1);
                int radius = args.getAs(Integer.class);
                if (radius < 16 || radius > 128) {
                    throw new CommandInvalidStateException("Radius must be between 16 and 128 blocks");
                }
                settings.minimapRadius.value = radius;
                MinimapRenderer.invalidate();
                logDirect("Minimap radius set to " + radius + " blocks");
            }
            case "size" -> {
                if (!args.hasAny()) {
                    logDirect("Current minimap size: " + settings.minimapSize.value + "px");
                    return;
                }
                args.requireMax(1);
                int size = args.getAs(Integer.class);
                if (size < 64 || size > 256) {
                    throw new CommandInvalidStateException("Size must be between 64 and 256 pixels");
                }
                settings.minimapSize.value = size;
                logDirect("Minimap size set to " + size + "px");
            }
            case "pos", "position" -> {
                if (!args.hasAny()) {
                    logDirect("Current minimap position: " + settings.minimapPosition.value);
                    return;
                }
                args.requireMax(1);
                String pos = args.getString().toLowerCase(Locale.ROOT);
                if (!POSITIONS.contains(pos)) {
                    throw new CommandInvalidStateException("Invalid position '" + pos + "'. Expected one of: " + String.join(", ", POSITIONS));
                }
                settings.minimapPosition.value = pos;
                logDirect("Minimap position set to " + pos);
            }
            case "full", "fullscreen", "world" -> {
                args.requireMax(0);
                ctx.minecraft().execute(MinimapRenderer::openFullscreenMap);
            }
            case "rotate" -> {
                args.requireMax(0);
                settings.minimapRotate.value = !settings.minimapRotate.value;
                logDirect("Minimap rotation " + (settings.minimapRotate.value ? "enabled (heading-up)" : "disabled (north-up)"));
            }
            case "shape", "circular" -> {
                args.requireMax(0);
                settings.minimapCircular.value = !settings.minimapCircular.value;
                MinimapRenderer.invalidate();
                logDirect("Minimap shape set to " + (settings.minimapCircular.value ? "circular" : "square"));
            }
            case "waypoints", "wp" -> {
                args.requireMax(0);
                settings.minimapShowWaypoints.value = !settings.minimapShowWaypoints.value;
                logDirect("Minimap waypoints " + (settings.minimapShowWaypoints.value ? "enabled" : "disabled"));
            }
            case "key", "keybind" -> {
                if (!args.hasAny()) {
                    logDirect("Current fullscreen map keybind: " + settings.minimapFullscreenKey.value);
                    return;
                }
                args.requireMax(1);
                String key = args.getString().toUpperCase(Locale.ROOT);
                settings.minimapFullscreenKey.value = key;
                logDirect("Fullscreen map keybind set to " + key);
            }
            case "entities" -> {
                args.requireMax(0);
                settings.minimapShowEntities.value = !settings.minimapShowEntities.value;
                logDirect("Minimap entities " + (settings.minimapShowEntities.value ? "enabled" : "disabled"));
            }
            case "path" -> {
                args.requireMax(0);
                settings.minimapShowPath.value = !settings.minimapShowPath.value;
                logDirect("Minimap path overlay " + (settings.minimapShowPath.value ? "enabled" : "disabled"));
            }
            default -> throw new CommandInvalidStateException(
                    "Unknown subcommand '" + sub + "'. Use: " + String.join(", ", SUBCOMMANDS)
            );
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return new TabCompleteHelper()
                    .append(SUBCOMMANDS.stream())
                    .filterPrefix(args.getString())
                    .stream();
        }
        if (args.has(2)) {
            String sub = args.getString().toLowerCase(Locale.ROOT);
            if (args.hasExactlyOne()) {
                if ("mode".equals(sub)) {
                    return new TabCompleteHelper()
                            .append(MODES.stream())
                            .filterPrefix(args.getString())
                            .stream();
                }
                if ("pos".equals(sub) || "position".equals(sub)) {
                    return new TabCompleteHelper()
                            .append(POSITIONS.stream())
                            .filterPrefix(args.getString())
                            .stream();
                }
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Toggle or configure the HUD minimap";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Displays a top-down minimap showing terrain blocks, entities, and the current path/goal.",
                "In auto mode, automatically switches to cave layer when underground and player Y-layer while mining.",
                "",
                "Usage:",
                "> map - Toggle the minimap on or off",
                "> map <on|off|toggle> - Explicitly enable, disable, or toggle the minimap",
                "> map full - Open the interactive fullscreen world map",
                "> map mode <auto|surface|cave|layer> - Set the minimap view mode",
                "> map radius <16-128> - Set the block radius shown on the minimap",
                "> map size <64-256> - Set the on-screen size in GUI pixels",
                "> map pos <topright|topleft|bottomright|bottomleft> - Set screen corner",
                "> map rotate - Toggle heading-up rotation vs north-locked",
                "> map shape - Toggle circular vs square minimap shape",
                "> map waypoints - Toggle waypoint and death markers",
                "> map key <key> - Set keybind for the fullscreen world map (default: M)",
                "> map entities - Toggle entity markers on the minimap",
                "> map path - Toggle path and goal overlay on the minimap"
        );
    }
}
