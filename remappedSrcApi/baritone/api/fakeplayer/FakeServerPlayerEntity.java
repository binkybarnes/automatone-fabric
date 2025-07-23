/*
 * Requiem
 * Copyright (C) 2017-2021 Ladysnake
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses>.
 *
 * Linking this mod statically or dynamically with other
 * modules is making a combined work based on this mod.
 * Thus, the terms and conditions of the GNU General Public License cover the whole combination.
 *
 * In addition, as a special exception, the copyright holders of
 * this mod give you permission to combine this mod
 * with free software programs or libraries that are released under the GNU LGPL
 * and with code included in the standard release of Minecraft under All Rights Reserved (or
 * modified versions of such code, with unchanged license).
 * You may copy and distribute such a system following the terms of the GNU GPL for this mod
 * and the licenses of the other code concerned.
 *
 * Note that people who make modified versions of this mod are not obligated to grant
 * this special exception for their modified versions; it is their choice whether to do so.
 * The GNU General Public License gives permission to release a modified version without this exception;
 * this exception also makes it possible to release a modified version which carries forward this exception.
 */
package baritone.api.fakeplayer;

import baritone.api.utils.IEntityAccessor;
import com.google.common.base.Preconditions;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import javax.annotation.CheckForNull;
import java.util.Objects;
import java.util.UUID;

public class FakeServerPlayerEntity extends ServerPlayer implements AutomatoneFakePlayer {

    protected @Nullable GameProfile displayProfile;
    private boolean release;

    public FakeServerPlayerEntity(EntityType<? extends Player> type, ServerLevel world) {
        this(type, world, new GameProfile(UUID.randomUUID(), "FakePlayer"));
    }

    public FakeServerPlayerEntity(EntityType<? extends Player> type, ServerLevel world, GameProfile profile) {
        super(world.getServer(), world, profile);
        ((IEntityAccessor)this).automatone$setType(type);
        this.setMaxUpStep(0.6f); // same step height as LivingEntity
        // Side effects go brr
        new ServerGamePacketListenerImpl(world.getServer(), new Connection(PacketFlow.SERVERBOUND), this);
    }

    public void selectHotbarSlot(int hotbarSlot) {
        Preconditions.checkArgument(Inventory.isHotbarSlot(hotbarSlot));
        if (this.getInventory().selected != hotbarSlot && this.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            this.stopUsingItem();
        }

        this.getInventory().selected = hotbarSlot;
        this.resetLastActionTime();
    }

    public void swapHands() {
        ItemStack offhandStack = this.getItemInHand(InteractionHand.OFF_HAND);
        this.setItemInHand(InteractionHand.OFF_HAND, this.getItemInHand(InteractionHand.MAIN_HAND));
        this.setItemInHand(InteractionHand.MAIN_HAND, offhandStack);
        this.stopUsingItem();
    }

    /**
     * Calls {@link #stopUsingItem()} at the end of the tick if nothing re-activated it
     */
    public void releaseActiveItem() {
        this.release = true;
    }

    public void useItem(InteractionHand hand) {
        if (this.release && hand != this.getUsedItemHand()) {
            this.stopUsingItem();
        }

        if (this.isUsingItem()) return;

        ItemStack stack = this.getItemInHand(hand);

        if (!stack.isEmpty()) {
            InteractionResult actionResult = this.gameMode.useItem(
                this,
                this.level(),
                stack,
                hand
            );

            if (actionResult.shouldSwing()) {
                this.swing(hand, true);
            }
        }
    }

    @Override
    public void tick() {
        this.closeContainer();
        super.tick();
        this.doTick();
    }

    @Override
    public void aiStep() {
        if (this.isInWater() && this.isShiftKeyDown() && this.isAffectedByFluids()) {
            // Mirrors ClientPlayerEntity's sinking behaviour
            this.goDownInWater();
        }
        super.aiStep();
    }

    @Override
    protected void serverAiStep() {
        super.serverAiStep();
        if (this.release) {
            this.stopUsingItem();
            this.release = false;
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        this.attack(target);
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (this.hurtMarked) {
            super.knockback(strength, x, z);
        }
    }

    @Override
    protected void checkFallDamage(double heightDifference, boolean onGround, BlockState landedState, BlockPos landedPosition) {
        this.doCheckFallDamage(0, heightDifference, 0, onGround);
    }

    @Override
    public boolean isSleepingLongEnough() {
        return true;    // Fake players do not delay the sleep of other players
    }

    /**
     * Controls whether this should be considered a player for ticking and tracking purposes
     *
     * <p>We want fake players to behave like regular entities, so for once we pretend they are not players.
     */
    @Override
    public boolean isAlwaysTicking() {
        return false;
    }

    @Override
    public Component getName() {
        GameProfile displayProfile = this.getDisplayProfile();
        if (displayProfile != null) {
            return Component.literal(displayProfile.getName());
        }
        return super.getName();
    }

    @Nullable
    public GameProfile getDisplayProfile() {
        return this.displayProfile;
    }

    public void setDisplayProfile(@CheckForNull GameProfile profile) {
        if (!Objects.equals(profile, this.displayProfile)) {
            this.displayProfile = profile;
            this.sendProfileUpdatePacket();
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("automatone:display_profile", NbtType.COMPOUND)) {
            this.displayProfile = NbtUtils.readGameProfile(tag.getCompound("automatone:display_profile"));
        }
        if (tag.contains("head_yaw")) {
            this.yHeadRot = tag.getFloat("head_yaw");
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.displayProfile != null) {
            tag.put("automatone:display_profile", NbtUtils.writeGameProfile(new CompoundTag(), this.displayProfile));
        }
        tag.putFloat("head_yaw", this.yHeadRot);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        FriendlyByteBuf buf = PacketByteBufs.create();
        writeToSpawnPacket(buf);
        return new ClientboundCustomPayloadPacket(FakePlayers.SPAWN_PACKET_ID, buf);
    }

    protected void writeToSpawnPacket(FriendlyByteBuf buf) {
        buf.writeVarInt(this.getId());
        buf.writeUUID(this.getUUID());
        buf.writeVarInt(BuiltInRegistries.ENTITY_TYPE.getId(this.getType()));
        buf.writeUtf(this.getGameProfile().getName());
        buf.writeDouble(this.getX());
        buf.writeDouble(this.getY());
        buf.writeDouble(this.getZ());
        buf.writeByte((byte)((int)(this.getYRot() * 256.0F / 360.0F)));
        buf.writeByte((byte)((int)(this.getXRot() * 256.0F / 360.0F)));
        buf.writeByte((byte)((int)(this.yHeadRot * 256.0F / 360.0F)));
        writeProfile(buf, this.getDisplayProfile());
    }

    public void sendProfileUpdatePacket() {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(this.getId());
        writeProfile(buf, this.getDisplayProfile());

        ClientboundCustomPayloadPacket packet = new ClientboundCustomPayloadPacket(FakePlayers.PROFILE_UPDATE_PACKET_ID, buf);

        for (ServerPlayer e : PlayerLookup.tracking(this)) {
            e.connection.send(packet);
        }
    }

    public static void writeProfile(FriendlyByteBuf buf, @Nullable GameProfile profile) {
        buf.writeBoolean(profile != null);

        if (profile != null) {
            buf.writeUUID(profile.getId());
            buf.writeUtf(profile.getName());
        }
    }
}
