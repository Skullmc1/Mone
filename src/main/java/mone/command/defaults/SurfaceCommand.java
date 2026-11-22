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
import mone.api.pathing.goals.Goal;
import mone.api.pathing.goals.GoalBlock;
import mone.api.utils.BetterBlockPos;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.world.level.block.AirBlock;

public class SurfaceCommand extends Command {

    protected SurfaceCommand(IMone Mone) {
        super(Mone, "surface", "top");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        final BetterBlockPos playerPos = ctx.playerFeet();
        final int maxY = ctx.world().getMaxY();

        if (!ctx.world().dimensionType().hasCeiling() && ctx.world().canSeeSky(playerPos.above())) {
            logDirect("Already at surface");
            return;
        }

        // In normal dimensions, scan downward from build height for the highest solid ground with sky access and 2-block headroom
        if (!ctx.world().dimensionType().hasCeiling()) {
            for (int y = maxY - 2; y > playerPos.getY(); y--) {
                BetterBlockPos ground = new BetterBlockPos(playerPos.getX(), y, playerPos.getZ());
                BetterBlockPos feet = ground.above();
                BetterBlockPos head = feet.above();
                if (!(ctx.world().getBlockState(ground).getBlock() instanceof AirBlock)
                        && ctx.world().getBlockState(feet).getBlock() instanceof AirBlock
                        && ctx.world().getBlockState(head).getBlock() instanceof AirBlock
                        && ctx.world().canSeeSky(feet)) {
                    Goal goal = new GoalBlock(feet);
                    logDirect(String.format("Heading to the surface at (%d, %d, %d).", feet.x, feet.y, feet.z));
                    Mone.getCustomGoalProcess().setGoalAndPath(goal);
                    return;
                }
            }
        } else {
            // In ceiling dimensions (e.g. Nether), scan upward for the first 2-high air space above solid ground
            int ceilingLimit = ctx.world().dimensionType().logicalHeight() - 3;
            for (int y = playerPos.getY() + 1; y < ceilingLimit; y++) {
                BetterBlockPos ground = new BetterBlockPos(playerPos.getX(), y, playerPos.getZ());
                BetterBlockPos feet = ground.above();
                BetterBlockPos head = feet.above();
                if (!(ctx.world().getBlockState(ground).getBlock() instanceof AirBlock)
                        && ctx.world().getBlockState(feet).getBlock() instanceof AirBlock
                        && ctx.world().getBlockState(head).getBlock() instanceof AirBlock) {
                    Goal goal = new GoalBlock(feet);
                    logDirect(String.format("Heading to higher ground at (%d, %d, %d).", feet.x, feet.y, feet.z));
                    Mone.getCustomGoalProcess().setGoalAndPath(goal);
                    return;
                }
            }
        }
        logDirect("No higher surface location found.");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Used to get out of caves, mines, ...";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The surface/top command tells Mone to head towards the closest surface-like area.",
                "",
                "This can be the surface or the highest available air space, depending on circumstances.",
                "",
                "Usage:",
                "> surface - Used to get out of caves, mines, ...",
                "> top - Used to get out of caves, mines, ..."
        );
    }
}
