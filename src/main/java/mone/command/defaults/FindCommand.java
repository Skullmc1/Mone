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
import mone.api.command.datatypes.BlockById;
import mone.api.command.exception.CommandException;
import mone.api.command.helpers.TabCompleteHelper;
import mone.api.utils.BetterBlockPos;
import mone.cache.CachedChunk;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static mone.api.command.IMoneChatControl.FORCE_COMMAND_PREFIX;

public class FindCommand extends Command {

    public FindCommand(IMone Mone) {
        super(Mone, "find");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMin(1);
        List<Block> toFind = new ArrayList<>();
        while (args.hasAny()) {
            toFind.add(args.getDatatypeFor(BlockById.INSTANCE));
        }
        BetterBlockPos origin = ctx.playerFeet();
        List<BetterBlockPos> positions = toFind.stream()
                .flatMap(block ->
                        ctx.worldData().getCachedWorld().getLocationsOf(
                                BuiltInRegistries.BLOCK.getKey(block).getPath(),
                                200,
                                origin.x,
                                origin.z,
                                4
                        ).stream()
                )
                .map(BetterBlockPos::new)
                .distinct()
                .sorted(Comparator.comparingDouble(origin::distSqr))
                .collect(Collectors.toList());
        if (!positions.isEmpty()) {
            int shown = Math.min(positions.size(), 20);
            logDirect(String.format("Found %d cached location(s) (showing closest %d):", positions.size(), shown));
            for (int i = 0; i < shown; i++) {
                logDirect(positionToComponent(positions.get(i), origin));
            }
        } else {
            logDirect("No positions known, are you sure the blocks are cached?");
        }
    }

    private Component positionToComponent(BetterBlockPos pos, BetterBlockPos origin) {
        int dist = (int) Math.round(Math.sqrt(origin.distSqr(pos)));
        String positionText = String.format("%s %s %s", pos.x, pos.y, pos.z);
        String command = String.format("%sgoto %s", FORCE_COMMAND_PREFIX, positionText);
        MutableComponent baseComponent = Component.literal(String.format("(%d, %d, %d) - %d blocks away", pos.x, pos.y, pos.z, dist));
        MutableComponent hoverComponent = Component.literal("Click to go to this position");
        baseComponent.setStyle(baseComponent.getStyle()
                .withColor(ChatFormatting.GRAY)
                .withInsertion(positionText)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(hoverComponent)));
        return baseComponent;
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        return new TabCompleteHelper()
                .append(
                        CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.stream()
                                .map(BuiltInRegistries.BLOCK::getKey)
                                .map(Object::toString)
                )
                .filterPrefixNamespaced(args.getString())
                .sortAlphabetically()
                .stream();
    }

    @Override
    public String getShortDesc() {
        return "Find positions of a certain block";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The find command searches through Mone's cache and attempts to find the location of the block.",
                "Tab completion will suggest only cached blocks and uncached blocks can not be found.",
                "",
                "Usage:",
                "> find <block> [...] - Try finding the listed blocks"
        );
    }
}
