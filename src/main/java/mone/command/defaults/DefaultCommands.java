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
import mone.api.command.ICommand;

import java.util.*;

public final class DefaultCommands {

    private DefaultCommands() {
    }

    public static List<ICommand> createAll(IMone Mone) {
        Objects.requireNonNull(Mone);
        List<ICommand> commands = new ArrayList<>(Arrays.asList(
                new HelpCommand(Mone),
                new SetCommand(Mone),
                new CommandAlias(Mone, Arrays.asList("modified", "mod", "Mone", "modifiedsettings"), "List modified settings", "set modified"),
                new CommandAlias(Mone, "reset", "Reset all settings or just one", "set reset"),
                new GoalCommand(Mone),
                new GotoCommand(Mone),
                new PathCommand(Mone),
                new ProcCommand(Mone),
                new ETACommand(Mone),
                new VersionCommand(Mone),
                new RepackCommand(Mone),
                new BuildCommand(Mone),
                //new SchematicaCommand(Mone),
                new LitematicaCommand(Mone),
                new ComeCommand(Mone),
                new AxisCommand(Mone),
                new ForceCancelCommand(Mone),
                new GcCommand(Mone),
                new InvertCommand(Mone),
                new TunnelCommand(Mone),
                new RenderCommand(Mone),
                new FarmCommand(Mone),
                new FollowCommand(Mone),
                new PickupCommand(Mone),
                new ExploreFilterCommand(Mone),
                new ReloadAllCommand(Mone),
                new SaveAllCommand(Mone),
                new ExploreCommand(Mone),
                new BlacklistCommand(Mone),
                new FindCommand(Mone),
                new MineCommand(Mone),
                new ClickCommand(Mone),
                new SurfaceCommand(Mone),
                new ThisWayCommand(Mone),
                new WaypointsCommand(Mone),
                new CommandAlias(Mone, "sethome", "Sets your home waypoint", "waypoints save home"),
                new CommandAlias(Mone, "home", "Path to your home waypoint", "waypoints goto home"),
                new SelCommand(Mone),
                new ElytraCommand(Mone)
        ));
        ExecutionControlCommands prc = new ExecutionControlCommands(Mone);
        commands.add(prc.pauseCommand);
        commands.add(prc.resumeCommand);
        commands.add(prc.pausedCommand);
        commands.add(prc.cancelCommand);
        return Collections.unmodifiableList(commands);
    }
}
