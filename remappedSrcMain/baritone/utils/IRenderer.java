/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.awt.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static org.lwjgl.opengl.GL11.*;

public interface IRenderer {

    Tesselator tessellator = Tesselator.getInstance();
    BufferBuilder buffer = tessellator.getBuilder();
    EntityRenderDispatcher renderManager = Minecraft.getInstance().getEntityRenderDispatcher();
    Settings settings = BaritoneAPI.getGlobalSettings();

    static void glColor(Color color, float alpha) {
        float[] colorComponents = color.getColorComponents(null);
        State.red = colorComponents[0];
        State.green = colorComponents[1];
        State.blue = colorComponents[2];
        State.alpha = alpha;
    }

    static void startLines(Color color, float alpha, float lineWidth, boolean ignoreDepth) {
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
        glColor(color, alpha);
        RenderSystem.lineWidth(lineWidth);
        RenderSystem.depthMask(false);

        if (ignoreDepth) {
            RenderSystem.disableDepthTest();
        }
    }

    static void startLines(Color color, float lineWidth, boolean ignoreDepth) {
        startLines(color, .4f, lineWidth, ignoreDepth);
    }

    static void endLines(boolean ignoredDepth) {
        if (ignoredDepth) {
            RenderSystem.enableDepthTest();
        }

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    static void drawAABB(AABB aabb) {
        Vec3 cameraPos = renderManager.camera.getPosition();
        AABB toDraw = aabb.move(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        VertexConsumer vertexConsumer3 = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(vertexConsumer3, toDraw.minX, toDraw.minY, toDraw.minZ, toDraw.maxX, toDraw.maxY, toDraw.maxZ, State.red, State.green, State.blue, State.alpha);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        // bottom
        buffer.vertex((float) toDraw.minX, (float) toDraw.minY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.minY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.minY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.minY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.minY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.minX, (float) toDraw.minY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.minX, (float) toDraw.minY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.minX, (float) toDraw.minY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        // top
        buffer.vertex((float) toDraw.minX, (float) toDraw.maxY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.maxY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.maxY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.maxY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.maxY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.minX, (float) toDraw.maxY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.minX, (float) toDraw.maxY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.minX, (float) toDraw.maxY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        // corners
        buffer.vertex((float) toDraw.minX, (float) toDraw.minY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.minX, (float) toDraw.maxY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.minY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.maxY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.minY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.maxY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.minX, (float) toDraw.minY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) toDraw.minX, (float) toDraw.maxY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        tessellator.end();
    }

    static void drawAABB(AABB aabb, double expand) {
        drawAABB(aabb.inflate(expand, expand, expand));
    }

    class State {
        static float red, green, blue, alpha;
    }
}
