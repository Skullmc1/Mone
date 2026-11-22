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
import mone.api.command.Command;
import mone.api.command.argument.IArgConsumer;
import mone.api.command.exception.CommandException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class HighwayCommand extends Command {

    public HighwayCommand(IMone mone) {
        super(mone, "highway");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(2);
        int width = args.hasAny() ? args.getAs(Integer.class) : 3;
        int height = args.hasAny() ? args.getAs(Integer.class) : 3;
        ((Mone) Mone).getHighwayProcess().start(ctx.player().getDirection(), width, height);
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Build a highway tunnel and bridge gaps in your facing direction";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Mines a tunnel in your facing direction and bridges air/lava floor gaps using throwaway blocks.",
                "",
                "Usage:",
                "> highway [width] [height] - Start building a highway (default: 3x3)"
        );
    }
}
