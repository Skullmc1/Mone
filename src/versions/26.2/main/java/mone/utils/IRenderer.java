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

import mone.api.MoneAPI;
import mone.api.Settings;
import mone.utils.accessor.IEntityRenderManager;
import mone.utils.accessor.IRenderPipelines;
import mone.utils.accessor.IRenderType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.function.BiFunction;

public interface IRenderer {

    // TODO: MC 26.2 removed Tesselator. Replace with BufferBuilder + ByteBufferBuilder pattern.

    static IEntityRenderManager getRenderManager() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? null : (IEntityRenderManager) mc.getEntityRenderDispatcher();
    }

    Settings settings = MoneAPI.getSettings();
    BlendFunction Mone_LINES_BLEND = new BlendFunction(
        BlendFactor.SRC_ALPHA,
        BlendFactor.ONE_MINUS_SRC_ALPHA,
        BlendFactor.ONE,
        BlendFactor.ZERO
    );

    RenderPipeline.Snippet Mone_LINES_SNIPPET = RenderPipeline.builder(((IRenderPipelines) new RenderPipelines()).getLinesSnippet())
        .withColorTargetState(new ColorTargetState(Mone_LINES_BLEND))
        .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
        .withCull(false)
        .buildSnippet();

    RenderPipeline.Snippet Mone_BEACON_BEAM_SNIPPET = RenderPipeline.builder(((IRenderPipelines) new RenderPipelines()).getMatricesFogSnippet())
            .withVertexShader("core/rendertype_beacon_beam")
            .withFragmentShader("core/rendertype_beacon_beam")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexBinding(0, DefaultVertexFormat.BLOCK)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .buildSnippet();

    RenderPipeline BEACON_BEAM_OPAQUE = ((IRenderPipelines) new RenderPipelines()).Mone$registerPipeline(RenderPipeline.builder(Mone_BEACON_BEAM_SNIPPET)
            .withLocation("pipeline/mone_beacon_beam_opaque")
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(true)
            .build());

    RenderPipeline BEACON_BEAM_TRANSLUCENT = ((IRenderPipelines) new RenderPipelines()).Mone$registerPipeline(RenderPipeline.builder(Mone_BEACON_BEAM_SNIPPET)
            .withLocation("pipeline/mone_beacon_beam_translucent")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(true)
            .build());

    RenderType linesWithDepthRenderType = ((IRenderType) RenderTypes.lines()).createRenderType(
        "renderType/mone_lines_with_depth",
        RenderSetup.builder(RenderPipeline.builder(Mone_LINES_SNIPPET)
            .withLocation("pipelines/mone_lines_with_depth")
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build())
            .createRenderSetup()
    );
    RenderType linesNoDepthRenderType = ((IRenderType) RenderTypes.lines()).createRenderType(
        "renderType/mone_lines_no_depth",
        RenderSetup.builder(RenderPipeline.builder(Mone_LINES_SNIPPET)
                .withLocation("pipelines/mone_lines_no_depth")
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .build())
            .createRenderSetup()
    );


    BiFunction<Identifier, Boolean, RenderType> BEACON_BEAM = Util.memoize(
            (identifier, boolean_) -> ((IRenderType) RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, boolean_)).createRenderType(
                    boolean_ ? "renderType/mone_beacon_beam_translucent" : "renderType/mone_beacon_beam_opaque",
            RenderSetup.builder(boolean_ ? BEACON_BEAM_TRANSLUCENT : BEACON_BEAM_OPAQUE)
                    .withTexture("Sampler0", identifier)
                    .sortOnUpload()
                    .createRenderSetup())
    );

    float[] color = new float[]{1.0F, 1.0F, 1.0F, 255.0F};

    static void glColor(Color color, float alpha) {
        float[] colorComponents = color.getColorComponents(null);
        IRenderer.color[0] = colorComponents[0];
        IRenderer.color[1] = colorComponents[1];
        IRenderer.color[2] = colorComponents[2];
        IRenderer.color[3] = alpha;
    }

    static BufferBuilder startLines(Color color, float alpha) {
        glColor(color, alpha);
        return new BufferBuilder(new ByteBufferBuilder(16, 256), PrimitiveTopology.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
    }

    static BufferBuilder startLines(Color color) {
        return startLines(color, .4f);
    }

    static void endLines(BufferBuilder bufferBuilder, boolean ignoredDepth) {
    }

    static BufferBuilder startBlockQuads() {
        return new BufferBuilder(new ByteBufferBuilder(16, 256), PrimitiveTopology.QUADS, DefaultVertexFormat.BLOCK);
    }

    static void endBuffer(BufferBuilder bufferBuilder, RenderType renderType) {
    }

    static void emitLine(BufferBuilder bufferBuilder, PoseStack stack, double x1, double y1, double z1, double x2, double y2, double z2, float lineWidth) {
        IEntityRenderManager rm = getRenderManager();
        if (rm == null) return;
        int col = ((int)(color[3] * 255) << 24) | ((int)(color[0] * 255) << 16) | ((int)(color[1] * 255) << 8) | (int)(color[2] * 255);
        Gizmos.line(
                new Vec3(x1 + rm.renderPosX(), y1 + rm.renderPosY(), z1 + rm.renderPosZ()),
                new Vec3(x2 + rm.renderPosX(), y2 + rm.renderPosY(), z2 + rm.renderPosZ()),
                col, lineWidth
        );
    }

    static void emitLine(BufferBuilder bufferBuilder, PoseStack stack,
                         double x1, double y1, double z1,
                         double x2, double y2, double z2,
                         double nx, double ny, double nz,
                         float lineWidth
    ) {
        IEntityRenderManager rm = getRenderManager();
        if (rm == null) return;
        int col = ((int)(color[3] * 255) << 24) | ((int)(color[0] * 255) << 16) | ((int)(color[1] * 255) << 8) | (int)(color[2] * 255);
        Gizmos.line(
                new Vec3(x1 + rm.renderPosX(), y1 + rm.renderPosY(), z1 + rm.renderPosZ()),
                new Vec3(x2 + rm.renderPosX(), y2 + rm.renderPosY(), z2 + rm.renderPosZ()),
                col, lineWidth
        );
    }

    static void emitLine(BufferBuilder bufferBuilder, PoseStack stack,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float nx, float ny, float nz,
                         float lineWidth
    ) {
        IEntityRenderManager rm = getRenderManager();
        if (rm == null) return;
        int col = ((int)(color[3] * 255) << 24) | ((int)(color[0] * 255) << 16) | ((int)(color[1] * 255) << 8) | (int)(color[2] * 255);
        Gizmos.line(
                new Vec3(x1 + rm.renderPosX(), y1 + rm.renderPosY(), z1 + rm.renderPosZ()),
                new Vec3(x2 + rm.renderPosX(), y2 + rm.renderPosY(), z2 + rm.renderPosZ()),
                col, lineWidth
        );
    }

    static void emitAABB(BufferBuilder bufferBuilder, PoseStack stack, AABB aabb, float lineWidth) {
        IEntityRenderManager rm = getRenderManager();
        if (rm == null) return;
        int col = ((int)(color[3] * 255) << 24) | ((int)(color[0] * 255) << 16) | ((int)(color[1] * 255) << 8) | (int)(color[2] * 255);
        Gizmos.cuboid(aabb, GizmoStyle.stroke(col, lineWidth));
    }

    static void emitAABB(BufferBuilder bufferBuilder, PoseStack stack, AABB aabb, double expand, float lineWidth) {
        emitAABB(bufferBuilder, stack, aabb.inflate(expand, expand, expand), lineWidth);
    }

    static void emitLine(BufferBuilder bufferBuilder, PoseStack stack, Vec3 start, Vec3 end, float lineWidth) {
        IEntityRenderManager rm = getRenderManager();
        if (rm == null) return;
        int col = ((int)(color[3] * 255) << 24) | ((int)(color[0] * 255) << 16) | ((int)(color[1] * 255) << 8) | (int)(color[2] * 255);
        Gizmos.line(start, end, col, lineWidth);
    }

    static void emitTexturedVertex(BufferBuilder bufferBuilder, PoseStack.Pose pose, float x, float y, float z, int color, float u, float v, float nx, float ny, float nz) {
        bufferBuilder.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(pose, nx, ny, nz);
    }

    static RenderType beaconBeam(Identifier identifier, boolean bl) {
        return BEACON_BEAM.apply(identifier, bl);
    }

    static RenderType beaconBeam(Identifier identifier, boolean bl, boolean ignoreDepth) {
        return ignoreDepth ? beaconBeam(identifier, bl) : RenderTypes.beaconBeam(identifier, bl);
    }
}
