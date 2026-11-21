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

package mone.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Minecraft 26.3 compatibility helpers.
 */
public final class McCompat {

    private McCompat() {}

    public static void swing(LocalPlayer player, InteractionHand hand) {
        player.swing(hand, SwingAnimation.DEFAULT, false);
    }

    public static boolean isValidBonemeal(BonemealableBlock block, Level level, BlockPos pos, BlockState state) {
        return block.isValidBonemealTarget(level, pos, state, BonemealSource.INTERACTION)
                && block.isBonemealSuccess(level, level.getRandom(), pos, state, BonemealSource.INTERACTION);
    }

    public static boolean isNotPassiveEnderman(Entity entity) {
        return !(entity instanceof Enderman) || ((Enderman) entity).isCreepy();
    }

    public static void rotateYDegrees(PoseStack stack, float degrees) {
        stack.rotate(Axis.YP.rotationDegrees(degrees));
    }

    public static int getChunkX(ClientboundLevelChunkWithLightPacket packet) {
        return packet.x();
    }

    public static int getChunkZ(ClientboundLevelChunkWithLightPacket packet) {
        return packet.z();
    }
}
