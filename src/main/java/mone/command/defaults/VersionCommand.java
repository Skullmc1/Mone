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
import mone.api.command.exception.CommandInvalidStateException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class VersionCommand extends Command {

    public VersionCommand(IMone Mone) {
        super(Mone, "version");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        String version = resolveModVersion();
        logDirect(String.format("You are running Mone v%s", version));
    }

    private String resolveModVersion() {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            Object optContainer = loaderClass.getMethod("getModContainer", String.class).invoke(loader, "mone");
            if (optContainer instanceof java.util.Optional<?> opt && opt.isPresent()) {
                Object container = opt.get();
                Object metadata = container.getClass().getMethod("getMetadata").invoke(container);
                Object ver = metadata.getClass().getMethod("getVersion").invoke(metadata);
                Object friendly = ver.getClass().getMethod("getFriendlyString").invoke(ver);
                if (friendly != null && !friendly.toString().isBlank()) {
                    return friendly.toString();
                }
            }
        } catch (Throwable ignored) {
        }
        String impl = getClass().getPackage().getImplementationVersion();
        return (impl != null && !impl.isBlank()) ? impl : "dev";
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "View the Mone version";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The version command prints the version of Mone you're currently running.",
                "",
                "Usage:",
                "> version - View version information, if present"
        );
    }
}
