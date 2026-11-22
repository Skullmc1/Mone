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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class LogoutCommand extends Command {

    public LogoutCommand(IMone mone) {
        super(mone, "logout", "disconnect");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        Mone.getPathingBehavior().cancelEverything();
        ctx.minecraft().execute(() -> {
            if (ctx.world() instanceof ClientLevel cl) {
                cl.disconnect(Component.literal("Mone: Task completed, logging out."));
            }
        });
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Disconnect from the current server or world";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Cancels all active tasks and cleanly disconnects from the server/world.",
                "Useful as the final step of a #queue script.",
                "",
                "Usage:",
                "> logout"
        );
    }
}
