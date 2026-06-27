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

package mone.command.manager;

import mone.Mone;
import mone.api.IMone;
import mone.api.command.ICommand;
import mone.api.command.argument.ICommandArgument;
import mone.api.command.exception.CommandException;
import mone.api.command.exception.CommandUnhandledException;
import mone.api.command.exception.ICommandException;
import mone.api.command.helpers.TabCompleteHelper;
import mone.api.command.manager.ICommandManager;
import mone.api.command.registry.Registry;
import mone.command.argument.ArgConsumer;
import mone.command.argument.CommandArguments;
import mone.command.defaults.DefaultCommands;
import mone.api.utils.Pair;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;


/**
 * The default, internal implementation of {@link ICommandManager}
 *
 */
public class CommandManager implements ICommandManager {

    private final Registry<ICommand> registry = new Registry<>();
    private final Mone Mone;
    private boolean initialized;

    public CommandManager(Mone Mone) {
        this.Mone = Mone;
    }

    private void ensureInitialized() {
        if (!initialized) {
            initialized = true;
            DefaultCommands.createAll(Mone).forEach(this.registry::register);
        }
    }

    @Override
    public IMone getBaritone() {
        return this.Mone;
    }

    @Override
    public Registry<ICommand> getRegistry() {
        ensureInitialized();
        return this.registry;
    }

    @Override
    public ICommand getCommand(String name) {
        ensureInitialized();
        for (ICommand command : this.registry.entries) {
            if (command.getNames().contains(name.toLowerCase(Locale.US))) {
                return command;
            }
        }
        return null;
    }

    @Override
    public boolean execute(String string) {
        return this.execute(expand(string));
    }

    @Override
    public boolean execute(Pair<String, List<ICommandArgument>> expanded) {
        ExecutionWrapper execution = this.from(expanded);
        if (execution != null) {
            execution.execute();
        }
        return execution != null;
    }

    @Override
    public Stream<String> tabComplete(Pair<String, List<ICommandArgument>> expanded) {
        ExecutionWrapper execution = this.from(expanded);
        return execution == null ? Stream.empty() : execution.tabComplete();
    }

    @Override
    public Stream<String> tabComplete(String prefix) {
        Pair<String, List<ICommandArgument>> pair = expand(prefix, true);
        String label = pair.first();
        List<ICommandArgument> args = pair.second();
        if (args.isEmpty()) {
            return new TabCompleteHelper()
                    .addCommands(this.Mone.getCommandManager())
                    .filterPrefix(label)
                    .stream();
        } else {
            return tabComplete(pair);
        }
    }

    private ExecutionWrapper from(Pair<String, List<ICommandArgument>> expanded) {
        String label = expanded.first();
        ArgConsumer args = new ArgConsumer(this, expanded.second());

        ICommand command = this.getCommand(label);
        return command == null ? null : new ExecutionWrapper(command, label, args);
    }

    private static Pair<String, List<ICommandArgument>> expand(String string, boolean preserveEmptyLast) {
        String label = string.split("\\s", 2)[0];
        List<ICommandArgument> args = CommandArguments.from(string.substring(label.length()), preserveEmptyLast);
        return new Pair<>(label, args);
    }

    public static Pair<String, List<ICommandArgument>> expand(String string) {
        return expand(string, false);
    }

    private static final class ExecutionWrapper {

        private ICommand command;
        private String label;
        private ArgConsumer args;

        private ExecutionWrapper(ICommand command, String label, ArgConsumer args) {
            this.command = command;
            this.label = label;
            this.args = args;
        }

        private void execute() {
            try {
                this.command.execute(this.label, this.args);
            } catch (Throwable t) {
                // Create a handleable exception, wrap if needed
                ICommandException exception = t instanceof ICommandException
                        ? (ICommandException) t
                        : new CommandUnhandledException(t);

                exception.handle(command, args.getArgs());
            }
        }

        private Stream<String> tabComplete() {
            try {
                return this.command.tabComplete(this.label, this.args);
            } catch (CommandException ignored) {
                // NOP
            } catch (Throwable t) {
                t.printStackTrace();
            }
            return Stream.empty();
        }
    }
}
