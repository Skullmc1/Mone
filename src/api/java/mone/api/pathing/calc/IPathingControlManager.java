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

package mone.api.pathing.calc;

import mone.api.process.IMoneProcess;
import mone.api.process.PathingCommand;

import java.util.Optional;

/**
 */
public interface IPathingControlManager {

    /**
     * Registers a process with this pathing control manager. See {@link IMoneProcess} for more details.
     *
     * @param process The process
     * @see IMoneProcess
     */
    void registerProcess(IMoneProcess process);

    /**
     * @return The most recent {@link IMoneProcess} that had control
     */
    Optional<IMoneProcess> mostRecentInControl();

    /**
     * @return The most recent pathing command executed
     */
    Optional<PathingCommand> mostRecentCommand();
}
