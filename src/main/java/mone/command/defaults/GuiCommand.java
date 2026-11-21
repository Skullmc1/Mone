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
import mone.api.command.Command;
import mone.api.command.argument.IArgConsumer;
import mone.api.command.exception.CommandException;
import mone.utils.SettingsGuiScreen;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class GuiCommand extends Command {

    public GuiCommand(IMone mone) {
        super(mone, "gui", "config");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        ctx.minecraft().execute(SettingsGuiScreen::open);
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Open the in-game Mone Settings GUI";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Opens the searchable in-game Mone Settings GUI (also bound to K by default).",
                "",
                "Usage:",
                "> gui"
        );
    }
}
