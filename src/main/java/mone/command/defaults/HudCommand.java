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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class HudCommand extends Command {

    private static final List<String> SUBCOMMANDS = Arrays.asList("on", "off", "toggle");

    public HudCommand(IMone mone) {
        super(mone, "hud");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        Settings settings = MoneAPI.getSettings();
        if (!args.hasAny()) {
            settings.hud.value = !settings.hud.value;
            logDirect("HUD " + (settings.hud.value ? "enabled" : "disabled"));
            return;
        }
        args.requireMax(1);
        String sub = args.getString().toLowerCase(Locale.ROOT);
        switch (sub) {
            case "on", "enable", "show" -> {
                settings.hud.value = true;
                logDirect("HUD enabled");
            }
            case "off", "disable", "hide" -> {
                settings.hud.value = false;
                logDirect("HUD disabled");
            }
            case "toggle" -> {
                settings.hud.value = !settings.hud.value;
                logDirect("HUD " + (settings.hud.value ? "enabled" : "disabled"));
            }
            default -> throw new CommandInvalidStateException("Unknown subcommand '" + sub + "'. Use: on, off, toggle");
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
        return "Toggle the active-work HUD overlay";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Toggles the Mone HUD overlay. When enabled, displays a fading screen border and",
                "a top banner ('Mone is working. (...)') whenever a Mone process is active.",
                "",
                "Usage:",
                "> hud - Toggle the HUD on or off",
                "> hud <on|off|toggle> - Explicitly enable, disable, or toggle the HUD"
        );
    }
}
