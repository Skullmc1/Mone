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
import mone.api.cache.IWaypoint;
import mone.api.cache.IWorldData;
import mone.api.command.Command;
import mone.api.command.argument.IArgConsumer;
import mone.api.command.datatypes.ForBlockOptionalMeta;
import mone.api.command.datatypes.RelativeCoordinate;
import mone.api.command.datatypes.RelativeGoal;
import mone.api.command.exception.CommandException;
import mone.api.pathing.goals.Goal;
import mone.api.pathing.goals.GoalBlock;
import mone.api.utils.BetterBlockPos;
import mone.api.utils.BlockOptionalMeta;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class GotoCommand extends Command {

    protected GotoCommand(IMone Mone) {
        super(Mone, "goto");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        // If we have a numeric first argument, then parse arguments as coordinates.
        // Note: There is no reason to want to go where you're already at so there
        // is no need to handle the case of empty arguments.
        if (args.peekDatatypeOrNull(RelativeCoordinate.INSTANCE) != null) {
            args.requireMax(3);
            BetterBlockPos origin = ctx.playerFeet();
            Goal goal = args.getDatatypePost(RelativeGoal.INSTANCE, origin);
            logDirect(String.format("Going to: %s", goal.toString()));
            Mone.getCustomGoalProcess().setGoalAndPath(goal);
            return;
        }
        args.requireMax(1);
        BlockOptionalMeta destination = args.peekDatatypeOrNull(ForBlockOptionalMeta.INSTANCE);
        if (destination != null) {
            args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);
            Mone.getGetToBlockProcess().getToBlock(destination);
            return;
        }
        String wpName = args.peekString();
        IWorldData world = Mone.getWorldProvider().getCurrentWorld();
        if (world != null && world.getWaypoints() != null) {
            for (IWaypoint wp : world.getWaypoints().getAllWaypoints()) {
                if (wp.getName() != null && wp.getName().equalsIgnoreCase(wpName)) {
                    args.getString();
                    BetterBlockPos loc = wp.getLocation();
                    logDirect(String.format("Heading to waypoint '%s' (%d, %d, %d).", wp.getName(), loc.x, loc.y, loc.z));
                    Mone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(loc));
                    return;
                }
            }
        }
        // Fallback so standard error message is thrown for invalid block/waypoint
        Mone.getGetToBlockProcess().getToBlock(args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        args.requireMax(1);
        Stream<String> blocks = args.tabCompleteDatatype(ForBlockOptionalMeta.INSTANCE);
        IWorldData world = Mone.getWorldProvider().getCurrentWorld();
        if (world == null || world.getWaypoints() == null) {
            return blocks;
        }
        String prefix = args.hasAny() ? args.peekString().toLowerCase() : "";
        Stream<String> waypoints = world.getWaypoints().getAllWaypoints().stream()
                .map(IWaypoint::getName)
                .filter(name -> name != null && !name.isEmpty() && name.toLowerCase().startsWith(prefix))
                .distinct();
        return Stream.concat(waypoints, blocks);
    }

    @Override
    public String getShortDesc() {
        return "Go to a coordinate or block";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The goto command tells Mone to head towards a given goal or block.",
                "",
                "Wherever a coordinate is expected, you can use ~ just like in regular Minecraft commands. Or, you can just use regular numbers.",
                "",
                "Usage:",
                "> goto <block> - Go to a block, wherever it is in the world",
                "> goto <y> - Go to a Y level",
                "> goto <x> <z> - Go to an X,Z position",
                "> goto <x> <y> <z> - Go to an X,Y,Z position"
        );
    }
}
