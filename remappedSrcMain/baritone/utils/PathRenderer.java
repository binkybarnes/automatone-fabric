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

import baritone.Automatone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalInverted;
import baritone.api.pathing.goals.GoalTwoBlocks;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.pathing.goals.GoalYLevel;
import baritone.api.utils.interfaces.IGoalRenderPos;
import baritone.render.ClientPathingBehaviour;
import baritone.render.RenderedPath;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;

/**
 * @author Brady
 * @since 8/9/2018
 */
public final class PathRenderer implements IRenderer {

    private static final ResourceLocation TEXTURE_BEACON_BEAM = new ResourceLocation("textures/entity/beacon_beam.png");


    private PathRenderer() {}

    public static double posX() {
        return renderManager.camera.getPosition().x;
    }

    public static double posY() {
        return renderManager.camera.getPosition().y;
    }

    public static double posZ() {
        return renderManager.camera.getPosition().z;
    }

    public static void render(WorldRenderContext event, ClientPathingBehaviour behavior) {
        float partialTicks = event.tickDelta();
        Goal goal = behavior.getGoal();
        Minecraft mc = Minecraft.getInstance();

        DimensionType thisPlayerDimension = behavior.entity.level().dimensionType();
        Level world = Objects.requireNonNull(Minecraft.getInstance().level);
        DimensionType currentRenderViewDimension = world.dimensionType();

        if (thisPlayerDimension != currentRenderViewDimension) {
            // this is a path for a bot in a different dimension, don't render it
            return;
        }

        Entity renderView = mc.getCameraEntity();

        if (renderView.level() != world) {
            Automatone.LOGGER.error("I have no idea what's going on");
            Automatone.LOGGER.error("The primary baritone is in a different world than the render view entity");
            Automatone.LOGGER.error("Not rendering the path");
            return;
        }

        if (goal != null && settings.renderGoal.get()) {
            drawDankLitGoalBox(event.matrixStack(), renderView, goal, partialTicks, settings.colorGoalBox.get());
        }

        if (!settings.renderPath.get()) {
            return;
        }

        RenderedPath current = behavior.getCurrent(); // this should prevent most race conditions?
        RenderedPath next = behavior.getNext(); // like, now it's not possible for current!=null to be true, then suddenly false because of another thread
        if (current != null && settings.renderSelectionBoxes.get()) {
            drawManySelectionBoxes(renderView, current.toBreak(), settings.colorBlocksToBreak.get());
            drawManySelectionBoxes(renderView, current.toPlace(), settings.colorBlocksToPlace.get());
            drawManySelectionBoxes(renderView, current.toWalkInto(), settings.colorBlocksToWalkInto.get());
        }

        //drawManySelectionBoxes(player, Collections.singletonList(behavior.pathStart()), partialTicks, Color.WHITE);

        // Render the current path, if there is one
        if (current != null && current.pathPositions() != null) {
            int renderBegin = Math.max(current.getPosition() - 3, 0);
            drawPath(current.pathPositions(), renderBegin, settings.colorCurrentPath.get(), settings.fadePath.get(), 10, 20);
        }

        if (next != null && next.pathPositions() != null) {
            drawPath(next.pathPositions(), 0, settings.colorNextPath.get(), settings.fadePath.get(), 10, 20);
        }

        // If there is a path calculation currently running, render the path calculation process
        behavior.getInProgress().ifPresent(currentlyRunning -> {
            currentlyRunning.bestPathSoFar().ifPresent(p -> drawPath(p.positions(), 0, settings.colorBestPathSoFar.get(), settings.fadePath.get(), 10, 20));

            currentlyRunning.pathToMostRecentNodeConsidered().ifPresent(mr -> {
                drawPath(mr.positions(), 0, settings.colorMostRecentConsidered.get(), settings.fadePath.get(), 10, 20);
                drawManySelectionBoxes(renderView, Collections.singletonList(mr.getDest()), settings.colorMostRecentConsidered.get());
            });
        });
    }

    public static void drawPath(List<? extends BlockPos> positions, int startIndex, Color color, boolean fadeOut, int fadeStart0, int fadeEnd0) {
        IRenderer.startLines(color, settings.pathRenderLineWidthPixels.get(), settings.renderPathIgnoreDepth.get());

        int fadeStart = fadeStart0 + startIndex;
        int fadeEnd = fadeEnd0 + startIndex;

        for (int i = startIndex, next; i < positions.size() - 1; i = next) {
            BlockPos start = positions.get(i);
            BlockPos end = positions.get(next = i + 1);

            int dirX = end.getX() - start.getX();
            int dirY = end.getY() - start.getY();
            int dirZ = end.getZ() - start.getZ();

            while (next + 1 < positions.size() && (!fadeOut || next + 1 < fadeStart) &&
                    (dirX == positions.get(next + 1).getX() - end.getX() &&
                            dirY == positions.get(next + 1).getY() - end.getY() &&
                            dirZ == positions.get(next + 1).getZ() - end.getZ())) {
                end = positions.get(++next);
            }

            if (fadeOut) {
                float alpha;

                if (i <= fadeStart) {
                    alpha = 0.4F;
                } else {
                    if (i > fadeEnd) {
                        break;
                    }
                    alpha = 0.4F * (1.0F - (float) (i - fadeStart) / (float) (fadeEnd - fadeStart));
                }
                IRenderer.glColor(color, alpha);
            }

            drawLine(start.getX(), start.getY(), start.getZ(), end.getX(), end.getY(), end.getZ());

            tessellator.end();
        }

        IRenderer.endLines(settings.renderPathIgnoreDepth.get());
    }


    public static void drawLine(double x1, double y1, double z1, double x2, double y2, double z2) {

        double vpX = posX();
        double vpY = posY();
        double vpZ = posZ();
        boolean renderPathAsFrickinThingy = !settings.renderPathAsLine.get();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buffer.begin(renderPathAsFrickinThingy ? VertexFormat.Mode.DEBUG_LINE_STRIP : VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex((float) (x1 + 0.5D - vpX), (float) (y1 + 0.5D - vpY), (float) (z1 + 0.5D - vpZ)).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) (x2 + 0.5D - vpX), (float) (y2 + 0.5D - vpY), (float) (z2 + 0.5D - vpZ)).color(State.red, State.green, State.blue, State.alpha).endVertex();

        if (renderPathAsFrickinThingy) {
            buffer.vertex((float) (x2 + 0.5D - vpX), (float) (y2 + 0.53D - vpY), (float) (z2 + 0.5D - vpZ)).color(State.red, State.green, State.blue, State.alpha).endVertex();
            buffer.vertex((float) (x1 + 0.5D - vpX), (float) (y1 + 0.53D - vpY), (float) (z1 + 0.5D - vpZ)).color(State.red, State.green, State.blue, State.alpha).endVertex();
            buffer.vertex((float) (x1 + 0.5D - vpX), (float) (y1 + 0.5D - vpY), (float) (z1 + 0.5D - vpZ)).color(State.red, State.green, State.blue, State.alpha).endVertex();
        }
    }

    public static void drawManySelectionBoxes(Entity player, Collection<BlockPos> positions, Color color) {
        IRenderer.startLines(color, settings.pathRenderLineWidthPixels.get(), settings.renderSelectionBoxesIgnoreDepth.get());

        positions.forEach(pos -> {
            BlockState state = player.level().getBlockState(pos);
            VoxelShape shape = state.getShape(player.level(), pos);
            AABB toDraw = shape.isEmpty() ? Shapes.block().bounds() : shape.bounds();
            toDraw = toDraw.move(pos);
            IRenderer.drawAABB(toDraw, .002D);
        });

        IRenderer.endLines(settings.renderSelectionBoxesIgnoreDepth.get());
    }

    public static void drawDankLitGoalBox(PoseStack stack, Entity player, Goal goal, float partialTicks, Color color) {
        double renderPosX = posX();
        double renderPosY = posY();
        double renderPosZ = posZ();
        double minX, maxX;
        double minZ, maxZ;
        double minY, maxY;
        double y1, y2;
        double y = Mth.cos((float) (((float) ((System.nanoTime() / 100000L) % 20000L)) / 20000F * Math.PI * 2));
        if (goal instanceof IGoalRenderPos) {
            BlockPos goalPos = ((IGoalRenderPos) goal).getGoalPos();
            minX = goalPos.getX() + 0.002 - renderPosX;
            maxX = goalPos.getX() + 1 - 0.002 - renderPosX;
            minZ = goalPos.getZ() + 0.002 - renderPosZ;
            maxZ = goalPos.getZ() + 1 - 0.002 - renderPosZ;
            if (goal instanceof GoalGetToBlock || goal instanceof GoalTwoBlocks) {
                y /= 2;
            }
            y1 = 1 + y + goalPos.getY() - renderPosY;
            y2 = 1 - y + goalPos.getY() - renderPosY;
            minY = goalPos.getY() - renderPosY;
            maxY = minY + 2;
            if (goal instanceof GoalGetToBlock || goal instanceof GoalTwoBlocks) {
                y1 -= 0.5;
                y2 -= 0.5;
                maxY--;
            }
        } else if (goal instanceof GoalXZ goalPos) {

            if (settings.renderGoalXZBeacon.get()) {
                glPushAttrib(GL_LIGHTING_BIT);

                Minecraft.getInstance().getTextureManager().bindForSetup(TEXTURE_BEACON_BEAM);
                if (settings.renderGoalIgnoreDepth.get()) {
                    RenderSystem.disableDepthTest();
                }

                stack.pushPose(); // push
                stack.translate(goalPos.getX() - renderPosX, -renderPosY, goalPos.getZ() - renderPosZ); // translate

                BeaconRenderer.renderBeaconBeam(
                        stack,
                        Minecraft.getInstance().renderBuffers().bufferSource(),
                        TEXTURE_BEACON_BEAM,
                        partialTicks,
                        1.0F,
                        player.level().getGameTime(),
                        0,
                        player.level().getHeight(),
                        color.getColorComponents(null),

                        // Arguments filled by the private method lol
                        0.2F,
                        0.25F
                );

                stack.popPose(); // pop

                if (settings.renderGoalIgnoreDepth.get()) {
                    RenderSystem.enableDepthTest();
                }

                glPopAttrib();
                return;
            }

            minX = goalPos.getX() + 0.002 - renderPosX;
            maxX = goalPos.getX() + 1 - 0.002 - renderPosX;
            minZ = goalPos.getZ() + 0.002 - renderPosZ;
            maxZ = goalPos.getZ() + 1 - 0.002 - renderPosZ;

            y1 = 0;
            y2 = 0;
            minY = 0 - renderPosY;
            maxY = player.level().getHeight() - renderPosY;
        } else if (goal instanceof GoalComposite) {
            for (Goal g : ((GoalComposite) goal).goals()) {
                drawDankLitGoalBox(stack, player, g, partialTicks, color);
            }
            return;
        } else if (goal instanceof GoalInverted) {
            drawDankLitGoalBox(stack, player, ((GoalInverted) goal).origin, partialTicks, settings.colorInvertedGoalBox.get());
            return;
        } else if (goal instanceof GoalYLevel goalpos) {
            minX = player.getX() - settings.yLevelBoxSize.get() - renderPosX;
            minZ = player.getZ() - settings.yLevelBoxSize.get() - renderPosZ;
            maxX = player.getX() + settings.yLevelBoxSize.get() - renderPosX;
            maxZ = player.getZ() + settings.yLevelBoxSize.get() - renderPosZ;
            minY = ((GoalYLevel) goal).level - renderPosY;
            maxY = minY + 2;
            y1 = 1 + y + goalpos.level - renderPosY;
            y2 = 1 - y + goalpos.level - renderPosY;
        } else {
            return;
        }

        IRenderer.startLines(color, settings.goalRenderLineWidthPixels.get(), settings.renderGoalIgnoreDepth.get());

        renderHorizontalQuad(minX, maxX, minZ, maxZ, y1);
        renderHorizontalQuad(minX, maxX, minZ, maxZ, y2);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex((float) minX, (float) minY, (float) minZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) minX, (float) maxY, (float) minZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) maxX, (float) minY, (float) minZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) maxX, (float) maxY, (float) minZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) maxX, (float) minY, (float) maxZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) maxX, (float) maxY, (float) maxZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) minX, (float) minY, (float) maxZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        buffer.vertex((float) minX, (float) maxY, (float) maxZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
        tessellator.end();

        IRenderer.endLines(settings.renderGoalIgnoreDepth.get());
    }

    private static void renderHorizontalQuad(double minX, double maxX, double minZ, double maxZ, double y) {
        if (y != 0) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            buffer.vertex((float) minX, (float) y, (float) minZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
            buffer.vertex((float) maxX, (float) y, (float) minZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
            buffer.vertex((float) maxX, (float) y, (float) maxZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
            buffer.vertex((float) minX, (float) y, (float) maxZ).color(State.red, State.green, State.blue, State.alpha).endVertex();
            tessellator.end();
        }
    }
}
