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

package mone.api.event.listener;

import mone.api.event.events.*;

/**
 * An implementation of {@link IGameEventListener} that has all methods
 * overridden with empty bodies, allowing inheritors of this class to choose
 * which events they would like to listen in on.
 *
 * @see IGameEventListener
 */
public interface AbstractGameEventListener extends IGameEventListener {

    @Override
    default void onTick(TickEvent event) {}

    @Override
    default void onPostTick(TickEvent event) {}

    @Override
    default void onPlayerUpdate(PlayerUpdateEvent event) {}

    @Override
    default void onSendChatMessage(ChatEvent event) {}

    @Override
    default void onPreTabComplete(TabCompleteEvent event) {}

    @Override
    default void onChunkEvent(ChunkEvent event) {}

    @Override
    default void onBlockChange(BlockChangeEvent event) {}

    @Override
    default void onRenderPass(RenderEvent event) {}

    @Override
    default void onWorldEvent(WorldEvent event) {}

    @Override
    default void onSendPacket(PacketEvent event) {}

    @Override
    default void onReceivePacket(PacketEvent event) {}

    @Override
    default void onPlayerRotationMove(RotationMoveEvent event) {}

    @Override
    default void onPlayerSprintState(SprintStateEvent event) {}

    @Override
    default void onBlockInteract(BlockInteractEvent event) {}

    @Override
    default void onPlayerDeath() {}

    @Override
    default void onPathEvent(PathEvent event) {}
}
