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

package mone.api.schematic;

import mone.api.command.registry.Registry;
import mone.api.schematic.format.ISchematicFormat;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 */
public interface ISchematicSystem {

    /**
     * @return The registry of supported schematic formats
     */
    Registry<ISchematicFormat> getRegistry();

    /**
     * Attempts to find an {@link ISchematicFormat} that supports the specified schematic file.
     *
     * @param file A schematic file
     * @return The corresponding format for the file, {@link Optional#empty()} if no candidates were found.
     */
    Optional<ISchematicFormat> getByFile(File file);

    /**
     * @return A list of file extensions used by supported formats
     */
    List<String> getFileExtensions();
}
