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
import mone.api.command.helpers.TabCompleteHelper;
import mone.behavior.QueueBehavior;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class QueueCommand extends Command {

    private static final List<String> SUBCOMMANDS = Arrays.asList("add", "list", "skip", "clear", "pause", "resume");

    public QueueCommand(IMone mone) {
        super(mone, "queue", "q");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        QueueBehavior qb = ((Mone) Mone).getQueueBehavior();
        if (!args.hasAny()) {
            printQueue(qb);
            return;
        }

        String first = args.peekString().toLowerCase(Locale.ROOT);
        switch (first) {
            case "list", "status" -> {
                args.getString();
                args.requireMax(0);
                printQueue(qb);
            }
            case "clear", "reset" -> {
                args.getString();
                args.requireMax(0);
                qb.clear();
                logDirect("[Queue] Cleared all queued tasks.");
            }
            case "skip", "next" -> {
                args.getString();
                args.requireMax(0);
                qb.skip();
                logDirect("[Queue] Skipping to next queued task.");
            }
            case "pause" -> {
                args.getString();
                args.requireMax(0);
                qb.pause();
                logDirect("[Queue] Paused.");
            }
            case "resume" -> {
                args.getString();
                args.requireMax(0);
                qb.resume();
                logDirect("[Queue] Resumed.");
            }
            case "add" -> {
                args.getString();
                args.requireMin(1);
                String chain = args.rawRest();
                int count = qb.addChain(chain);
                logDirect("[Queue] Added " + count + " task(s). Total queued: " + qb.getQueuedCommands().size());
            }
            default -> {
                String chain = args.rawRest();
                int count = qb.addChain(chain);
                logDirect("[Queue] Queued " + count + " task(s). Total queued: " + qb.getQueuedCommands().size());
            }
        }
    }

    private void printQueue(QueueBehavior qb) {
        List<String> items = qb.getQueuedCommands();
        String cur = qb.getCurrentCommand();
        logDirect("[Queue] State: " + (qb.isPaused() ? "PAUSED" : (qb.isRunning() ? "RUNNING" : "IDLE")));
        if (cur != null) {
            logDirect("  Current: " + cur);
        }
        if (items.isEmpty()) {
            logDirect("  Upcoming: (empty)");
        } else {
            for (int i = 0; i < items.size(); i++) {
                logDirect("  " + (i + 1) + ". " + items.get(i));
            }
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
        return "Queue multiple commands to run sequentially";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Chains multiple Mone commands sequentially, starting the next task once the current one finishes.",
                "",
                "Usage:",
                "> queue <cmd1; cmd2; ...> - Queue semicolon-separated commands",
                "> queue add <cmd> - Append a command to the queue",
                "> queue list - Show queued commands",
                "> queue skip - Cancel current task and run the next",
                "> queue pause / resume - Pause or resume queue progression",
                "> queue clear - Clear all queued commands"
        );
    }
}
