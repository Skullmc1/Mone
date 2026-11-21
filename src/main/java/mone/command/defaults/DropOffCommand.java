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

import mone.Mone;
import mone.api.IMone;
import mone.api.MoneAPI;
import mone.api.Settings;
import mone.api.cache.IWaypoint;
import mone.api.cache.IWorldData;
import mone.api.cache.Waypoint;
import mone.api.command.Command;
import mone.api.command.argument.IArgConsumer;
import mone.api.command.exception.CommandException;
import mone.api.command.exception.CommandInvalidStateException;
import mone.api.command.helpers.TabCompleteHelper;
import mone.api.utils.BetterBlockPos;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class DropOffCommand extends Command {

    private static final List<String> SUBCOMMANDS = Arrays.asList("on", "off", "now", "set", "waypoint");

    public DropOffCommand(IMone mone) {
        super(mone, "dropoff");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        Settings settings = MoneAPI.getSettings();
        if (!args.hasAny()) {
            settings.autoDropOff.value = !settings.autoDropOff.value;
            logDirect("Smart Auto-Drop-Off " + (settings.autoDropOff.value ? "enabled" : "disabled")
                    + " (waypoint: " + settings.autoDropOffWaypoint.value + ")");
            return;
        }

        String sub = args.getString().toLowerCase(Locale.ROOT);
        switch (sub) {
            case "on", "enable" -> {
                args.requireMax(0);
                settings.autoDropOff.value = true;
                logDirect("Smart Auto-Drop-Off enabled (waypoint: " + settings.autoDropOffWaypoint.value + ")");
            }
            case "off", "disable" -> {
                args.requireMax(0);
                settings.autoDropOff.value = false;
                logDirect("Smart Auto-Drop-Off disabled");
            }
            case "now", "run" -> {
                args.requireMax(0);
                ((Mone) Mone).getAutoDropOffProcess().triggerNow();
            }
            case "set" -> {
                args.requireMax(0);
                IWorldData wd = Mone.getWorldProvider().getCurrentWorld();
                if (wd == null || wd.getWaypoints() == null) {
                    throw new CommandInvalidStateException("No world loaded");
                }
                BetterBlockPos pos = ctx.playerFeet();
                String wpName = settings.autoDropOffWaypoint.value;
                if (wpName == null || wpName.isBlank()) {
                    wpName = "storage";
                    settings.autoDropOffWaypoint.value = wpName;
                }
                // Replace existing waypoint with the same name
                for (IWaypoint existing : List.copyOf(wd.getWaypoints().getAllWaypoints())) {
                    if (wpName.equalsIgnoreCase(existing.getName())) {
                        wd.getWaypoints().removeWaypoint(existing);
                    }
                }
                wd.getWaypoints().addWaypoint(new Waypoint(wpName, IWaypoint.Tag.USER, pos));
                logDirect("Set drop-off storage waypoint '" + wpName + "' at " + pos.x + ", " + pos.y + ", " + pos.z);
            }
            case "waypoint", "wp" -> {
                args.requireExactly(1);
                String name = args.getString();
                settings.autoDropOffWaypoint.value = name;
                logDirect("Auto-Drop-Off storage waypoint set to '" + name + "'");
            }
            default -> throw new CommandInvalidStateException("Unknown subcommand '" + sub + "'. Use: on, off, now, set, waypoint <name>");
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
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Configure or trigger Smart Auto-Drop-Off to storage chests";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Automatically paths to a storage waypoint to deposit mined/farmed items when your inventory fills up.",
                "",
                "Usage:",
                "> dropoff - Toggle Smart Auto-Drop-Off on or off",
                "> dropoff set - Save your current location (next to a chest) as the storage waypoint",
                "> dropoff now - Trigger a drop-off run immediately",
                "> dropoff waypoint <name> - Set the waypoint name to deposit into"
        );
    }
}
