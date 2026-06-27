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

package mone.api;

import mone.api.cache.IWorldScanner;
import mone.api.command.ICommand;
import mone.api.command.ICommandSystem;
import mone.api.schematic.ISchematicSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;
import java.util.Objects;

/**
 * Provides the present {@link IMone} instances, as well as non-Mone instance related APIs.
 *
 */
public interface IMoneProvider {

    /**
     * Returns the primary {@link IMone} instance. This instance is persistent, and
     * is represented by the local player that is created by the game itself, not a "bot"
     * player through Mone.
     *
     * @return The primary {@link IMone} instance.
     */
    IMone getPrimaryMone();

    /**
     * Returns all of the active {@link IMone} instances. This includes the local one
     * returned by {@link #getPrimaryMone()}.
     *
     * @return All active {@link IMone} instances.
     * @see #getMoneForPlayer(LocalPlayer)
     */
    List<IMone> getAllMones();

    /**
     * Provides the {@link IMone} instance for a given {@link LocalPlayer}.
     *
     * @param player The player
     * @return The {@link IMone} instance.
     */
    default IMone getMoneForPlayer(LocalPlayer player) {
        for (IMone Mone : this.getAllMones()) {
            if (Objects.equals(player, Mone.getPlayerContext().player())) {
                return Mone;
            }
        }
        return null;
    }

    /**
     * Provides the {@link IMone} instance for a given {@link Minecraft}.
     *
     * @param minecraft The minecraft
     * @return The {@link IMone} instance.
     */
    default IMone getMoneForMinecraft(Minecraft minecraft) {
        for (IMone Mone : this.getAllMones()) {
            if (Objects.equals(minecraft, Mone.getPlayerContext().minecraft())) {
                return Mone;
            }
        }
        return null;
    }

    /**
     * Provides the {@link IMone} instance for the player with the specified connection.
     *
     * @param connection The connection
     * @return The {@link IMone} instance.
     */
    default IMone getMoneForConnection(ClientPacketListener connection) {
        for (IMone Mone : this.getAllMones()) {
            final LocalPlayer player = Mone.getPlayerContext().player();
            if (player != null && player.connection == connection) {
                return Mone;
            }
        }
        return null;
    }

    /**
     * Creates and registers a new {@link IMone} instance using the specified {@link Minecraft}. The existing
     * instance is returned if already registered.
     *
     * @param minecraft The minecraft
     * @return The {@link IMone} instance
     */
    IMone createMone(Minecraft minecraft);

    /**
     * Destroys and removes the specified {@link IMone} instance. If the specified instance is the
     * {@link #getPrimaryMone() primary Mone}, this operation has no effect and will return {@code false}.
     *
     * @param Mone The Mone instance to remove
     * @return Whether the Mone instance was removed
     */
    boolean destroyMone(IMone Mone);

    /**
     * Returns the {@link IWorldScanner} instance. This is not a type returned by
     * {@link IMone} implementation, because it is not linked with {@link IMone}.
     *
     * @return The {@link IWorldScanner} instance.
     */
    IWorldScanner getWorldScanner();

    /**
     * Returns the {@link ICommandSystem} instance. This is not bound to a specific {@link IMone}
     * instance because {@link ICommandSystem} itself controls global behavior for {@link ICommand}s.
     *
     * @return The {@link ICommandSystem} instance.
     */
    ICommandSystem getCommandSystem();

    /**
     * @return The {@link ISchematicSystem} instance.
     */
    ISchematicSystem getSchematicSystem();
}
