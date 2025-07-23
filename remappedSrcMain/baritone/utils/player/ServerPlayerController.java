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

package baritone.utils.player;

import baritone.api.utils.IPlayerController;
import baritone.utils.accessor.IServerPlayerInteractionManager;
import com.jamieswhiteshirt.reachentityattributes.ReachEntityAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;


/**
 * Implementation of {@link IPlayerController} that chains to the primary player controller's methods
 *
 * @author Brady
 * @since 12/14/2018
 */
public class ServerPlayerController implements IPlayerController {
    private final ServerPlayer player;
    private int sequence;

    public ServerPlayerController(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public boolean hasBrokenBlock() {
        return ((IServerPlayerInteractionManager) this.player.gameMode).automatone$hasBrokenBlock();
    }

    @Override
    public boolean onPlayerDamageBlock(BlockPos pos, Direction side) {
        IServerPlayerInteractionManager interactionManager = (IServerPlayerInteractionManager) this.player.gameMode;
        if (interactionManager.isMining()) {
            int progress = interactionManager.getBlockBreakingProgress();
            if (progress >= 10) {
                this.player.gameMode.handleBlockBreakAction(interactionManager.getMiningPos(), ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, side, this.player.level().getMaxBuildHeight(), sequence++);
            }
            return true;
        }
        return false;
    }

    @Override
    public void resetBlockRemoving() {
        IServerPlayerInteractionManager interactionManager = (IServerPlayerInteractionManager) this.player.gameMode;
        if (interactionManager.isMining()) {
            this.player.gameMode.handleBlockBreakAction(interactionManager.getMiningPos(), ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, Direction.UP, this.player.level().getMaxBuildHeight(), sequence++);
        }
    }

    @Override
    public GameType getGameType() {
        return player.gameMode.getGameModeForPlayer();
    }

    @Override
    public InteractionResult processRightClickBlock(Player player, Level world, InteractionHand hand, BlockHitResult result) {
        return this.player.gameMode.useItemOn(this.player, this.player.level(), this.player.getItemInHand(hand), hand, result);
    }

    @Override
    public InteractionResult processRightClick(Player player, Level world, InteractionHand hand) {
        return this.player.gameMode.useItem(this.player, this.player.level(), this.player.getItemInHand(hand), hand);
    }

    @Override
    public boolean clickBlock(BlockPos loc, Direction face) {
        BlockState state = this.player.level().getBlockState(loc);
        if (state.isAir()) return false;

        this.player.gameMode.handleBlockBreakAction(loc, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, face, this.player.level().getMaxBuildHeight(), sequence++);
        // Success = starting the mining process or insta-mining
        return ((IServerPlayerInteractionManager) this.player.gameMode).isMining() || this.player.level().isEmptyBlock(loc);
    }

    @Override
    public void setHittingBlock(boolean hittingBlock) {
        // NO-OP
    }

    @Override
    public double getBlockReachDistance() {
        return ReachEntityAttributes.getReachDistance(this.player, this.getGameType().isCreative() ? 5.0 : 4.5);
    }
}
