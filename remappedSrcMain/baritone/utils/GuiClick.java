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
import baritone.api.utils.BetterBlockPos;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.*;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;

import static baritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;
import static org.lwjgl.opengl.GL11.*;

public class GuiClick extends Screen {

    private final UUID callerUuid;
    private Matrix4f projectionViewMatrix;

    private BlockPos clickStart;
    private BlockPos currentMouseOver;

    public GuiClick(UUID callerUuid) {
        super(Component.literal("CLICK"));
        this.callerUuid = callerUuid;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        double mx = mc.mouseHandler.xpos();
        double my = mc.mouseHandler.ypos();

        my = mc.getWindow().getScreenHeight() - my;
        my *= mc.getWindow().getHeight() / (double) mc.getWindow().getScreenHeight();
        mx *= mc.getWindow().getWidth() / (double) mc.getWindow().getScreenWidth();
        Vec3 near = toWorld(mx, my, 0);
        Vec3 far = toWorld(mx, my, 1); // "Use 0.945 that's what stack overflow says" - leijurv

        if (near != null && far != null) {
            ///
            Vec3 viewerPos = new Vec3(PathRenderer.posX(), PathRenderer.posY(), PathRenderer.posZ());
            Player player = Objects.requireNonNull(Minecraft.getInstance().player);
            BlockHitResult result = player.level().clip(new ClipContext(near.add(viewerPos), far.add(viewerPos), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (result != null && result.getType() == HitResult.Type.BLOCK) {
                currentMouseOver = result.getBlockPos();
            }
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
        if (currentMouseOver != null) { //Catch this, or else a click into void will result in a crash
            Minecraft client = this.minecraft;
            assert client != null;
            assert client.player != null;
            assert client.level != null;
            if (mouseButton == 0) {
                if (clickStart != null && !clickStart.equals(currentMouseOver)) {
                    client.player.connection.sendCommand(String.format("execute as %s run automatone sel clear", callerUuid));
                    client.player.connection.sendCommand(String.format("execute as %s run automatone sel 1 %d %d %d", callerUuid, clickStart.getX(), clickStart.getY(), clickStart.getZ()));
                    client.player.connection.sendCommand(String.format("execute as %s run automatone sel 2 %d %d %d", callerUuid, currentMouseOver.getX(), currentMouseOver.getY(), currentMouseOver.getZ()));
                    MutableComponent component = Component.literal("").append(BaritoneAPI.getPrefix()).append(" Selection made! For usage: " + FORCE_COMMAND_PREFIX + "help sel");
                    component.setStyle(component.getStyle()
                            .applyFormat(ChatFormatting.WHITE)
                            .withClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    FORCE_COMMAND_PREFIX + "help sel"
                            )));
                    client.gui.getChat().addMessage(component);
                } else {
                    client.player.connection.sendCommand(String.format("execute as %s run automatone goto %d %d %d", callerUuid, currentMouseOver.getX(), currentMouseOver.getY(), currentMouseOver.getZ()));
                }
            } else if (mouseButton == 1) {
                client.player.connection.sendCommand(String.format("execute as %s run automatone goto %d %d %d", callerUuid, currentMouseOver.getX(), currentMouseOver.getY() + 1, currentMouseOver.getZ()));
            }
        }
        clickStart = null;
        return super.mouseReleased(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        clickStart = currentMouseOver;
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    public void onRender(PoseStack modelViewStack, Matrix4f projectionMatrix) {
        this.projectionViewMatrix = new Matrix4f(projectionMatrix);
        this.projectionViewMatrix.mul(modelViewStack.last().pose());
        this.projectionViewMatrix.invert();

        if (currentMouseOver != null) {
            Entity e = Minecraft.getInstance().getCameraEntity();
            Camera c = Minecraft.getInstance().gameRenderer.getMainCamera();
            assert e != null;
            VertexConsumer vertexConsumer = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderType.lines());
            LevelRenderer.renderLineBox(modelViewStack, vertexConsumer, new AABB(currentMouseOver).move(-c.getPosition().x, -c.getPosition().y, -c.getPosition().z).inflate(0.002), 0, 1, 1, 1);
            if (clickStart != null && !clickStart.equals(currentMouseOver)) {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
                RenderSystem.lineWidth(BaritoneAPI.getGlobalSettings().pathRenderLineWidthPixels.get());
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                RenderSystem.depthMask(false);
                RenderSystem.disableDepthTest();
                BetterBlockPos a = new BetterBlockPos(currentMouseOver);
                BetterBlockPos b = new BetterBlockPos(clickStart);
                LevelRenderer.renderLineBox(modelViewStack, vertexConsumer, new AABB(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z), Math.max(a.x, b.x) + 1, Math.max(a.y, b.y) + 1, Math.max(a.z, b.z) + 1).move(-c.getPosition().x, -c.getPosition().y, -c.getPosition().z), 1, 0, 0, 0.4f);
                RenderSystem.enableDepthTest();

                RenderSystem.depthMask(true);
                RenderSystem.disableBlend();
            }
        }
    }

    private Vec3 toWorld(double x, double y, double z) {
        if (this.projectionViewMatrix == null) {
            return null;
        }

        Window window = Minecraft.getInstance().getWindow();
        x /= window.getWidth();
        y /= window.getHeight();
        x = x * 2 - 1;
        y = y * 2 - 1;

        Vector4f pos = new Vector4f((float) x, (float) y, (float) z, 1.0F);
        pos.mul(this.projectionViewMatrix);
        if (pos.w == 0) {
            return null;
        }

        pos.div(pos.w); // Normalize projection coordinates
        return new Vec3(pos.x, pos.y, pos.z);
    }
}
