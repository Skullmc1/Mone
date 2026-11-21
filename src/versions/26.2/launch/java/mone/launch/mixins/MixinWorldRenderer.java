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

package mone.launch.mixins;

import mone.api.MoneAPI;
import mone.api.IMone;
import mone.api.event.events.RenderEvent;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 */
@Mixin(LevelRenderer.class)
public class MixinWorldRenderer {

    @Inject(
            method = "render",
            at = @At("RETURN")
    )
    private void onStartHand(final GraphicsResourceAllocator graphicsResourceAllocator, final DeltaTracker deltaTracker, final boolean bl, final CameraRenderState cameraRenderState, final Matrix4fc worldMatrix, final GpuBufferSlice gpuBufferSlice, final Vector4f vector4f, final boolean bl2, final CallbackInfo ci) {
        try (var scope = ((LevelRenderer) (Object) this).collectPerFrameRenderThreadGizmos()) {
            for (IMone IMone : MoneAPI.getProvider().getAllMones()) {
                PoseStack poseStack = new PoseStack();
                poseStack.mulPose(worldMatrix);
                Matrix4f projection = new Matrix4f(cameraRenderState.projectionMatrix);
                IMone.getGameEventHandler().onRenderPass(new RenderEvent(deltaTracker.getGameTimeDeltaPartialTick(false), poseStack, projection));
            }
        }
    }
}
